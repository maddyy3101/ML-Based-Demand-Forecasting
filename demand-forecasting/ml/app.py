from __future__ import annotations

import json
import os
import subprocess
import threading
from functools import lru_cache
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from flask import Flask, jsonify, request
from marshmallow import Schema, ValidationError, fields, validate

MODEL_DIR = Path(os.getenv("MODEL_DIR", Path(__file__).resolve().parent / "models"))
DEFAULT_DATASET = "powergrid_material_dataset.csv"

MATERIAL_THRESHOLDS = {
    "Cement": {"high": 8000, "low": 1500},
    "Conductor": {"high": 2500, "low": 500},
    "Insulator": {"high": 2000, "low": 400},
    "Steel": {"high": 5000, "low": 1000},
    "Transformer": {"high": 60, "low": 10},
}

MATERIAL_UNITS = {
    "Cement": "Bags",
    "Conductor": "Metres (coils)",
    "Insulator": "Units",
    "Steel": "MT",
    "Transformer": "Units",
}

REQUIRED_KEYS = [
    "Project_Phase",
    "State",
    "Region",
    "Terrain_Type",
    "Tower_Type",
    "Substation_Type",
    "Transmission_Length_KM",
    "Budget_Crore",
    "Lead_Time_Days",
    "Tax_Percentage",
    "Transportation_Cost",
    "Historical_Consumption",
    "Month",
    "Year",
    "Material_Type",
]


class PredictRequestSchema(Schema):
    Project_Phase = fields.String(
        required=True, validate=validate.OneOf(["Planning", "Execution", "Commissioning"])
    )
    State = fields.String(
        required=True,
        validate=validate.OneOf(
            [
                "Bihar",
                "Gujarat",
                "Karnataka",
                "Maharashtra",
                "Odisha",
                "Rajasthan",
                "Tamil Nadu",
                "Telangana",
                "Uttar Pradesh",
                "West Bengal",
            ]
        ),
    )
    Region = fields.String(required=True, validate=validate.OneOf(["East", "North", "South", "West"]))
    Terrain_Type = fields.String(required=True, validate=validate.OneOf(["Coastal", "Hilly", "Plain"]))
    Tower_Type = fields.String(required=True, validate=validate.OneOf(["220kV", "400kV", "765kV"]))
    Substation_Type = fields.String(required=True, validate=validate.OneOf(["AIS", "GIS"]))
    Transmission_Length_KM = fields.Integer(required=True, validate=validate.Range(min=50, max=298))
    Budget_Crore = fields.Integer(required=True, validate=validate.Range(min=302, max=1997))
    Lead_Time_Days = fields.Integer(required=True, validate=validate.Range(min=15, max=59))
    Tax_Percentage = fields.Float(required=True, validate=validate.Range(min=12.0, max=22.0))
    Transportation_Cost = fields.Float(required=True, validate=validate.Range(min=2608, max=30627))
    Historical_Consumption = fields.Float(required=True, validate=validate.Range(min=8, max=1800))
    Month = fields.Integer(required=True, validate=validate.Range(min=1, max=12))
    Year = fields.Integer(required=True, validate=validate.Range(min=2023, max=2030))
    Material_Type = fields.String(
        required=True,
        validate=validate.OneOf(["Cement", "Conductor", "Insulator", "Steel", "Transformer"]),
    )


class BatchRequestSchema(Schema):
    requests = fields.List(fields.Dict(), required=True, validate=validate.Length(min=1, max=50))


@lru_cache(maxsize=1)
def load_artifacts() -> dict[str, Any]:
    best_model = joblib.load(MODEL_DIR / "best_model.pkl")
    scaler = joblib.load(MODEL_DIR / "scaler.pkl")
    label_encoders = joblib.load(MODEL_DIR / "label_encoders.pkl")
    feature_names = joblib.load(MODEL_DIR / "feature_names.pkl")

    with open(MODEL_DIR / "model_metrics.json", "r", encoding="utf-8") as f:
        model_metrics = json.load(f)

    per_material_path = MODEL_DIR / "per_material_metrics.json"
    if per_material_path.exists():
        with open(per_material_path, "r", encoding="utf-8") as f:
            per_material_metrics = json.load(f)
    else:
        per_material_metrics = {}

    return {
        "model": best_model,
        "scaler": scaler,
        "label_encoders": label_encoders,
        "feature_names": feature_names,
        "model_metrics": model_metrics,
        "per_material_metrics": per_material_metrics,
    }


def encode_value(encoder: Any, value: str) -> int:
    try:
        return int(encoder.transform([str(value)])[0])
    except Exception:
        classes = list(getattr(encoder, "classes_", []))
        mapping = {label: idx for idx, label in enumerate(classes)}
        return int(mapping.get(str(value), 0))


def preprocess_input(data: dict[str, Any]) -> pd.DataFrame:
    artifacts = load_artifacts()
    encoders = artifacts["label_encoders"]
    feature_names = artifacts["feature_names"]

    month = int(data["Month"])
    year = int(data["Year"])

    payload = {
        "Project_Phase": encode_value(encoders["Project_Phase"], data["Project_Phase"]),
        "State": encode_value(encoders["State"], data["State"]),
        "Region": encode_value(encoders["Region"], data["Region"]),
        "Terrain_Type": encode_value(encoders["Terrain_Type"], data["Terrain_Type"]),
        "Tower_Type": encode_value(encoders["Tower_Type"], data["Tower_Type"]),
        "Substation_Type": encode_value(encoders["Substation_Type"], data["Substation_Type"]),
        "Transmission_Length_KM": int(data["Transmission_Length_KM"]),
        "Budget_Crore": int(data["Budget_Crore"]),
        "Lead_Time_Days": int(data["Lead_Time_Days"]),
        "Tax_Percentage": float(data["Tax_Percentage"]),
        "Transportation_Cost": float(data["Transportation_Cost"]),
        "Historical_Consumption": float(data["Historical_Consumption"]),
        "Month": month,
        "Year": year,
        "Material_Type": encode_value(encoders["Material_Type"], data["Material_Type"]),
        "quarter": int(pd.cut([month], bins=[0, 3, 6, 9, 12], labels=[1, 2, 3, 4])[0]),
        "month_sin": float(np.sin(2 * np.pi * month / 12)),
        "month_cos": float(np.cos(2 * np.pi * month / 12)),
        "fiscal_year": int(year if month >= 4 else year - 1),
    }

    row_df = pd.DataFrame([payload])
    ordered = row_df.reindex(columns=feature_names, fill_value=0)
    return ordered


def get_procurement_decision(predicted: int, material: str) -> dict[str, str]:
    threshold = MATERIAL_THRESHOLDS.get(material, {"high": 1000, "low": 100})

    if predicted > threshold["high"]:
        decision = "CRITICAL_PROCUREMENT"
        message = f"Demand critically high. Immediate procurement of {predicted} units required."
    elif predicted > threshold["high"] * 0.7:
        decision = "PROCURE_NOW"
        message = f"High demand forecast. Initiate procurement of {predicted} units this cycle."
    elif predicted < threshold["low"]:
        decision = "HOLD"
        message = "Low demand period. Current stock may suffice. Review before ordering."
    else:
        decision = "PLAN_ORDER"
        message = f"Moderate demand. Plan procurement of {predicted} units for next cycle."

    return {"decision": decision, "message": message}


def requires_scaling(model_type: str) -> bool:
    return "LinearRegression" in model_type


def predict_quantities(input_df: pd.DataFrame) -> np.ndarray:
    artifacts = load_artifacts()
    model = artifacts["model"]
    scaler = artifacts["scaler"]
    model_type = artifacts["model_metrics"].get("model_type", "")

    if requires_scaling(model_type):
        preds = model.predict(scaler.transform(input_df))
    else:
        preds = model.predict(input_df)

    return np.clip(np.asarray(preds, dtype=float), a_min=0, a_max=None)


def run_retraining(dataset_path: str) -> None:
    train_script = Path(__file__).resolve().parent / "demand_forecasting_train.py"
    cmd = ["python", str(train_script), "--data", dataset_path]
    subprocess.run(cmd, check=False)
    load_artifacts.cache_clear()


def create_app() -> Flask:
    app = Flask(__name__)

    @app.errorhandler(ValidationError)
    def handle_validation_error(err: ValidationError):
        return jsonify({"error": "Validation failed", "details": err.messages}), 400

    @app.errorhandler(Exception)
    def handle_exception(err: Exception):
        return jsonify({"error": "Internal server error", "message": str(err)}), 500

    @app.post("/predict")
    def predict():
        payload = request.get_json(silent=True) or {}
        validated = PredictRequestSchema().load(payload)

        features_df = preprocess_input(validated)
        predicted_quantity = int(round(float(predict_quantities(features_df)[0])))

        decision = get_procurement_decision(predicted_quantity, validated["Material_Type"])
        model_type = load_artifacts()["model_metrics"].get("model_type", "Unknown")

        return jsonify(
            {
                "quantity_required": predicted_quantity,
                "material_type": validated["Material_Type"],
                "unit_label": MATERIAL_UNITS.get(validated["Material_Type"], "Units"),
                "procurement_decision": decision["decision"],
                "decision_message": decision["message"],
                "model_type": model_type,
            }
        )

    @app.post("/predict/batch")
    def predict_batch():
        payload = request.get_json(silent=True) or {}
        base = BatchRequestSchema().load(payload)
        requests_payload = base["requests"]

        schema = PredictRequestSchema()
        valid_rows: list[dict[str, Any]] = []
        failed: list[dict[str, Any]] = []

        for index, item in enumerate(requests_payload):
            try:
                valid_rows.append(schema.load(item))
            except ValidationError as err:
                failed.append({"index": index, "errors": err.messages})

        if not valid_rows:
            return jsonify({"predictions": [], "count": 0, "failed": failed}), 400

        processed_frames = [preprocess_input(item) for item in valid_rows]
        batch_df = pd.concat(processed_frames, ignore_index=True)
        preds = predict_quantities(batch_df)

        results = []
        for row, pred in zip(valid_rows, preds):
            qty = int(round(float(pred)))
            decision = get_procurement_decision(qty, row["Material_Type"])
            results.append(
                {
                    "quantity_required": qty,
                    "material_type": row["Material_Type"],
                    "unit_label": MATERIAL_UNITS.get(row["Material_Type"], "Units"),
                    "procurement_decision": decision["decision"],
                    "decision_message": decision["message"],
                }
            )

        return jsonify({"predictions": results, "count": len(results), "failed": failed})

    @app.get("/health")
    def health():
        artifacts = load_artifacts()
        metrics = artifacts["model_metrics"]
        return jsonify(
            {
                "status": "ok",
                "model_type": metrics.get("model_type", "Unknown"),
                "features_count": len(artifacts["feature_names"]),
                "training_rows": metrics.get("train_rows", 0),
                "trained_at": metrics.get("trained_at"),
                "dataset": metrics.get("trained_on", DEFAULT_DATASET),
            }
        )

    @app.get("/model-info")
    def model_info():
        artifacts = load_artifacts()
        metrics = artifacts["model_metrics"]
        return jsonify(
            {
                "model_type": metrics.get("model_type", "Unknown"),
                "features": artifacts["feature_names"],
                "material_types": ["Cement", "Conductor", "Insulator", "Steel", "Transformer"],
                "project_phases": ["Planning", "Execution", "Commissioning"],
                "regions": ["East", "North", "South", "West"],
                "tower_types": ["220kV", "400kV", "765kV"],
                "metrics": metrics,
                "per_material_metrics": artifacts["per_material_metrics"],
            }
        )

    @app.get("/feature-importance")
    def feature_importance():
        artifacts = load_artifacts()
        model = artifacts["model"]
        feature_names = artifacts["feature_names"]

        if hasattr(model, "feature_importances_"):
            scores = np.asarray(model.feature_importances_, dtype=float)
        elif hasattr(model, "coef_"):
            raw_coef = np.asarray(model.coef_, dtype=float)
            scores = np.abs(raw_coef)
        else:
            return jsonify({"feature_importance": []})

        entries = [
            {"feature": feature, "importance": float(importance)}
            for feature, importance in zip(feature_names, scores)
        ]
        entries.sort(key=lambda item: item["importance"], reverse=True)

        return jsonify({"feature_importance": entries[:15]})

    @app.get("/accuracy")
    def accuracy():
        artifacts = load_artifacts()
        return jsonify(
            {
                "metrics": artifacts["model_metrics"],
                "per_material_metrics": artifacts["per_material_metrics"],
            }
        )

    @app.post("/retrain")
    def retrain():
        payload = request.get_json(silent=True) or {}
        dataset_path = payload.get("datasetPath", DEFAULT_DATASET)

        thread = threading.Thread(target=run_retraining, args=(dataset_path,), daemon=True)
        thread.start()

        return jsonify({"status": "started", "message": "Retraining initiated"})

    @app.post("/retrain/upload")
    def retrain_upload():
        csv_file = request.files.get("file")
        if csv_file is None:
            return jsonify({"error": "file is required"}), 400

        dataset_path = Path(__file__).resolve().parent / DEFAULT_DATASET
        csv_file.save(dataset_path)

        thread = threading.Thread(target=run_retraining, args=(str(dataset_path),), daemon=True)
        thread.start()

        return jsonify({"status": "started", "message": "Dataset received and retraining initiated"})

    return app


if __name__ == "__main__":
    application = create_app()
    application.run(host="0.0.0.0", port=5001)
