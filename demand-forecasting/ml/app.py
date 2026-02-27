"""
Flask Inference API — Demand Forecasting (Production)
"""

import os
import uuid
import logging
import numpy as np
import pandas as pd
import joblib

from datetime import datetime
from functools import lru_cache
from flask import Flask, request, jsonify, g
from marshmallow import Schema, fields, ValidationError, validates, validate

logging.basicConfig(
    level=logging.INFO,
    format='{"time":"%(asctime)s","level":"%(levelname)s","msg":"%(message)s"}',
    datefmt="%Y-%m-%dT%H:%M:%S"
)
logger = logging.getLogger(__name__)

MODEL_DIR = os.getenv("MODEL_DIR", "models")

@lru_cache(maxsize=1)
def load_artifacts():
    logger.info(f"Loading artifacts from {MODEL_DIR}")
    return {
        "model":         joblib.load(os.path.join(MODEL_DIR, "best_model.pkl")),
        "scaler":        joblib.load(os.path.join(MODEL_DIR, "scaler.pkl")),
        "le_map":        joblib.load(os.path.join(MODEL_DIR, "label_encoders.pkl")),
        "feature_names": joblib.load(os.path.join(MODEL_DIR, "feature_names.pkl")),
    }

VALID_CATEGORIES  = ["Electronics", "Clothing", "Furniture", "Groceries", "Toys"]
VALID_REGIONS     = ["North", "South", "East", "West"]
VALID_WEATHER     = ["Sunny", "Rainy", "Snowy", "Cloudy", "Windy"]
VALID_SEASONALITY = ["Spring", "Summer", "Autumn", "Winter"]

class PredictSchema(Schema):
    date               = fields.Date(required=True, format="%Y-%m-%d")
    category           = fields.Str(required=True, validate=validate.OneOf(VALID_CATEGORIES))
    region             = fields.Str(required=True, validate=validate.OneOf(VALID_REGIONS))
    inventory_level    = fields.Int(required=True, validate=validate.Range(min=0))
    units_sold         = fields.Int(load_default=0, validate=validate.Range(min=0))
    units_ordered      = fields.Int(required=True, validate=validate.Range(min=0))
    price              = fields.Float(required=True, validate=validate.Range(min=0.0))
    discount           = fields.Float(required=True, validate=validate.Range(min=0.0, max=100.0))
    weather_condition  = fields.Str(required=True, validate=validate.OneOf(VALID_WEATHER))
    promotion          = fields.Int(required=True, validate=validate.OneOf([0, 1]))
    competitor_pricing = fields.Float(required=True, validate=validate.Range(min=0.0))
    seasonality        = fields.Str(required=True, validate=validate.OneOf(VALID_SEASONALITY))
    epidemic           = fields.Int(required=True, validate=validate.OneOf([0, 1]))

predict_schema = PredictSchema()


def preprocess_input(data: dict, artifacts: dict) -> np.ndarray:
    le_map        = artifacts["le_map"]
    feature_names = artifacts["feature_names"]
    dt = data["date"]

    row = {
        "Inventory Level":    data["inventory_level"],
        "Units Sold":         data["units_sold"],
        "Units Ordered":      data["units_ordered"],
        "Price":              data["price"],
        "Discount":           data["discount"],
        "Promotion":          data["promotion"],
        "Competitor Pricing": data["competitor_pricing"],
        "Epidemic":           data["epidemic"],
        "year":               dt.year,
        "month":              dt.month,
        "day":                dt.day,
        "week":               int(dt.isocalendar()[1]),
        "dayofweek":          dt.weekday(),
        "quarter":            (dt.month - 1) // 3 + 1,
    }

    col_key_map = {
        "Category":          "category",
        "Region":            "region",
        "Weather Condition": "weather_condition",
        "Seasonality":       "seasonality",
    }
    for col, key in col_key_map.items():
        le = le_map[col]
        raw = str(data[key])
        try:
            row[col] = int(le.transform([raw])[0])
        except ValueError:
            logger.warning(f"Unseen label '{raw}' for '{col}' — defaulting to 0")
            row[col] = 0

    df_row = pd.DataFrame([row])[feature_names]
    return df_row.values


def create_app() -> Flask:
    app = Flask(__name__)
    app.config["MAX_BATCH_SIZE"] = int(os.getenv("MAX_BATCH_SIZE", 256))
    app.config["JSON_SORT_KEYS"] = False

    artifacts = load_artifacts()
    logger.info(f"Model ready: {type(artifacts['model']).__name__}")

    @app.before_request
    def attach_request_id():
        g.request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))

    @app.after_request
    def add_request_id_header(response):
        response.headers["X-Request-ID"] = g.request_id
        return response

    @app.errorhandler(400)
    def bad_request(e):
        return jsonify({"error": "Bad Request", "message": str(e),
                        "request_id": g.get("request_id")}), 400

    @app.errorhandler(404)
    def not_found(e):
        return jsonify({"error": "Not Found", "request_id": g.get("request_id")}), 404

    @app.errorhandler(500)
    def internal_error(e):
        logger.exception("Unhandled server error")
        return jsonify({"error": "Internal Server Error",
                        "request_id": g.get("request_id")}), 500

    @app.get("/health")
    def health():
        model = artifacts["model"]
        return jsonify({"status": "ok", "model_type": type(model).__name__,
                        "request_id": g.request_id})

    @app.get("/model/info")
    def model_info():
        model = artifacts["model"]
        info = {
            "model_type":    type(model).__name__,
            "feature_names": artifacts["feature_names"],
            "n_features":    len(artifacts["feature_names"]),
        }
        if hasattr(model, "n_estimators"):
            info["n_estimators"] = model.n_estimators
        return jsonify(info)

    @app.post("/predict")
    def predict():
        raw = request.get_json(force=True, silent=True)
        if raw is None:
            return jsonify({"error": "Request body must be valid JSON"}), 400
        try:
            data = predict_schema.load(raw)
        except ValidationError as err:
            return jsonify({"error": "Validation failed", "details": err.messages}), 422
        X = preprocess_input(data, artifacts)
        pred = float(artifacts["model"].predict(X)[0])
        logger.info(f"predict | request_id={g.request_id} | demand={pred:.2f}")
        return jsonify({"demand": round(pred, 2), "request_id": g.request_id})

    @app.post("/predict/batch")
    def predict_batch():
        raw = request.get_json(force=True, silent=True)
        if not isinstance(raw, list):
            return jsonify({"error": "Batch endpoint expects a JSON array"}), 400
        max_batch = app.config["MAX_BATCH_SIZE"]
        if len(raw) > max_batch:
            return jsonify({"error": f"Batch size {len(raw)} exceeds limit {max_batch}"}), 413
        results, valid_indices, rows = [], [], []
        for i, item in enumerate(raw):
            try:
                data = predict_schema.load(item)
                rows.append(preprocess_input(data, artifacts))
                valid_indices.append(i)
                results.append(None)
            except ValidationError as err:
                results.append({"demand": None, "status": "error", "message": err.messages})
        if rows:
            X_batch = np.vstack(rows)
            preds   = artifacts["model"].predict(X_batch)
            for idx, pred in zip(valid_indices, preds):
                results[idx] = {"demand": round(float(pred), 2), "status": "ok"}
        logger.info(f"batch_predict | request_id={g.request_id} | n={len(raw)} | valid={len(valid_indices)}")
        return jsonify(results)

    @app.get("/feature-importance")
    def feature_importance():
        model = artifacts["model"]
        if not hasattr(model, "feature_importances_"):
            return jsonify({"error": "Feature importances not available"}), 400
        fi = sorted(
            zip(artifacts["feature_names"], model.feature_importances_.tolist()),
            key=lambda x: x[1], reverse=True
        )
        return jsonify([{"feature": f, "importance": round(imp, 6)} for f, imp in fi])

    return app


if __name__ == "__main__":
    application = create_app()
    application.run(host="0.0.0.0", port=5000, debug=False)
