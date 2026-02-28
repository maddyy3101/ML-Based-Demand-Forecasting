"""
POWERGRID Material Demand Forecasting - ML Training Pipeline
Dataset  : powergrid_material_dataset.csv (72,000 rows, 17 cols)
Target   : Quantity_Required (units to procure per material per month)
Models   : Linear Regression, Random Forest, XGBoost (best auto-selected)
Problem  : PS#25193 - Ministry of Power / POWERGRID
"""

from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import joblib
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from scipy.stats import randint, uniform
from sklearn.ensemble import RandomForestRegressor
from sklearn.linear_model import LinearRegression
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import RandomizedSearchCV, TimeSeriesSplit
from sklearn.preprocessing import LabelEncoder, StandardScaler
from xgboost import XGBRegressor

DATA_PATH = "powergrid_material_dataset.csv"
MODEL_DIR = "models"
RANDOM_STATE = 42
TEST_RATIO = 0.20

Path(MODEL_DIR).mkdir(parents=True, exist_ok=True)

CATEGORICAL_COLUMNS = [
    "Project_Phase",
    "State",
    "Region",
    "Terrain_Type",
    "Tower_Type",
    "Substation_Type",
    "Material_Type",
]

FEATURE_COLUMNS = [
    "Project_Phase",
    "State",
    "Region",
    "Terrain_Type",
    "Tower_Type",
    "Substation_Type",
    "Material_Type",
    "Transmission_Length_KM",
    "Budget_Crore",
    "Lead_Time_Days",
    "Tax_Percentage",
    "Transportation_Cost",
    "Historical_Consumption",
    "Month",
    "Year",
    "quarter",
    "month_sin",
    "month_cos",
    "fiscal_year",
]

MATERIAL_ORDER = ["Cement", "Conductor", "Insulator", "Steel", "Transformer"]

TRAIN_ROWS = 0
TEST_ROWS = 0


def load_data(path: str) -> pd.DataFrame:
    df = pd.read_csv(path)
    print(f"\n[Data] Loaded: {path}")
    print(f"[Data] Shape: {df.shape} (expected (72000, 17))")
    print("\n[Data] Dtypes:\n", df.dtypes)
    print("\n[Data] Null counts:\n", df.isnull().sum())

    if df.isnull().any().any():
        raise ValueError("Dataset contains nulls. Expected no null values.")

    return df


def preprocess(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series, dict[str, LabelEncoder]]:
    working = df.copy()

    # Temporal features
    working["quarter"] = pd.cut(
        working["Month"], bins=[0, 3, 6, 9, 12], labels=[1, 2, 3, 4]
    ).astype(int)
    working["month_sin"] = np.sin(2 * np.pi * working["Month"] / 12)
    working["month_cos"] = np.cos(2 * np.pi * working["Month"] / 12)
    working["fiscal_year"] = working.apply(
        lambda r: int(r["Year"]) if int(r["Month"]) >= 4 else int(r["Year"]) - 1,
        axis=1,
    )

    # High-cardinality project key is non-ordinal; represented by other structural fields.
    working = working.drop(columns=["Project_ID"])

    X = working[FEATURE_COLUMNS].copy()
    y = working["Quantity_Required"].astype(float).copy()

    le_map: dict[str, LabelEncoder] = {col: LabelEncoder() for col in CATEGORICAL_COLUMNS}

    print("\n[Preprocess] Feature list:")
    for feature in FEATURE_COLUMNS:
        print(f" - {feature}")

    print("\n[Target describe]\n", y.describe())
    return X, y, le_map


def time_split(
    X: pd.DataFrame,
    y: pd.Series,
    test_ratio: float = TEST_RATIO,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.Series, pd.Series]:
    merged = X.copy()
    merged["__target__"] = y.values
    merged = merged.sort_values(["Year", "Month"], ascending=[True, True]).reset_index(drop=True)

    split_idx = int(len(merged) * (1.0 - test_ratio))
    train_df = merged.iloc[:split_idx].copy()
    test_df = merged.iloc[split_idx:].copy()

    X_train = train_df.drop(columns=["__target__"])
    X_test = test_df.drop(columns=["__target__"])
    y_train = train_df["__target__"]
    y_test = test_df["__target__"]

    print(
        f"Train: {len(X_train)} rows | Year range: "
        f"{int(X_train['Year'].min())}-{int(X_train['Year'].max())}"
    )
    print(
        f"Test:  {len(X_test)} rows  | Year range: "
        f"{int(X_test['Year'].min())}-{int(X_test['Year'].max())}"
    )

    return X_train, X_test, y_train, y_test


def encode_categoricals(
    X_train: pd.DataFrame,
    X_test: pd.DataFrame,
    le_map: dict[str, LabelEncoder],
) -> tuple[pd.DataFrame, pd.DataFrame, dict[str, LabelEncoder]]:
    X_train_enc = X_train.copy()
    X_test_enc = X_test.copy()

    for col in CATEGORICAL_COLUMNS:
        encoder = le_map[col]
        train_values = X_train_enc[col].astype(str)
        test_values = X_test_enc[col].astype(str)

        encoder.fit(train_values)
        classes = list(encoder.classes_)
        mapping = {label: idx for idx, label in enumerate(classes)}

        X_train_enc[col] = train_values.map(mapping).fillna(0).astype(int)
        X_test_enc[col] = test_values.map(mapping).fillna(0).astype(int)

    return X_train_enc, X_test_enc, le_map


def scale(
    X_train: pd.DataFrame,
    X_test: pd.DataFrame,
) -> tuple[np.ndarray, np.ndarray, StandardScaler]:
    scaler = StandardScaler()
    X_train_sc = scaler.fit_transform(X_train)
    X_test_sc = scaler.transform(X_test)
    return X_train_sc, X_test_sc, scaler


def evaluate(name: str, y_true: pd.Series, y_pred: np.ndarray) -> dict[str, float | str]:
    pred = np.clip(np.asarray(y_pred, dtype=float), a_min=0, a_max=None)
    y_arr = np.asarray(y_true, dtype=float)

    mae = float(mean_absolute_error(y_arr, pred))
    rmse = float(np.sqrt(mean_squared_error(y_arr, pred)))
    r2 = float(r2_score(y_arr, pred))
    mape = float(np.mean(np.abs(y_arr - pred) / np.maximum(y_arr, 1.0)) * 100.0)

    print(
        f"{name:<20} | MAE: {mae:10.3f} | RMSE: {rmse:10.3f} | "
        f"R2: {r2:8.4f} | MAPE: {mape:8.3f}%"
    )

    return {"model": name, "MAE": mae, "RMSE": rmse, "R2": r2, "MAPE": mape}


def train_models(
    X_train: pd.DataFrame,
    X_test: pd.DataFrame,
    y_train: pd.Series,
    y_test: pd.Series,
    X_train_sc: np.ndarray,
    X_test_sc: np.ndarray,
) -> tuple[pd.DataFrame, LinearRegression, RandomForestRegressor, XGBRegressor]:
    print("\n[Training] Baseline model run")
    results: list[dict[str, float | str]] = []

    lr = LinearRegression()
    lr.fit(X_train_sc, y_train)
    lr_preds = np.clip(lr.predict(X_test_sc), a_min=0, a_max=None)
    results.append(evaluate("LinearRegression", y_test, lr_preds))

    rf = RandomForestRegressor(
        n_estimators=300,
        max_depth=20,
        min_samples_leaf=3,
        n_jobs=-1,
        random_state=RANDOM_STATE,
    )
    rf.fit(X_train, y_train)
    rf_preds = np.clip(rf.predict(X_test), a_min=0, a_max=None)
    results.append(evaluate("RandomForest", y_test, rf_preds))

    xgb = XGBRegressor(
        n_estimators=400,
        learning_rate=0.05,
        max_depth=8,
        subsample=0.8,
        colsample_bytree=0.8,
        min_child_weight=3,
        tree_method="hist",
        random_state=RANDOM_STATE,
        n_jobs=-1,
        objective="reg:squarederror",
    )
    xgb.fit(X_train, y_train, eval_set=[(X_test, y_test)], verbose=False)
    xgb_preds = np.clip(xgb.predict(X_test), a_min=0, a_max=None)
    results.append(evaluate("XGBoost", y_test, xgb_preds))

    return pd.DataFrame(results), lr, rf, xgb


def tune_xgboost(
    X_train: pd.DataFrame,
    y_train: pd.Series,
    X_test: pd.DataFrame,
    y_test: pd.Series,
) -> XGBRegressor:
    print("\n[Tuning] XGBoost RandomizedSearchCV + TimeSeriesSplit")
    param_dist = {
        "n_estimators": randint(300, 700),
        "max_depth": randint(5, 12),
        "learning_rate": uniform(0.01, 0.15),
        "subsample": uniform(0.65, 0.35),
        "colsample_bytree": uniform(0.55, 0.45),
        "min_child_weight": randint(1, 8),
        "gamma": uniform(0.0, 0.4),
        "reg_alpha": uniform(0.0, 0.5),
        "reg_lambda": uniform(0.5, 1.5),
    }

    base_xgb = XGBRegressor(
        tree_method="hist",
        random_state=RANDOM_STATE,
        n_jobs=-1,
        objective="reg:squarederror",
    )

    search = RandomizedSearchCV(
        estimator=base_xgb,
        param_distributions=param_dist,
        n_iter=40,
        scoring="neg_root_mean_squared_error",
        cv=TimeSeriesSplit(n_splits=5),
        verbose=1,
        random_state=RANDOM_STATE,
        n_jobs=-1,
    )

    search.fit(X_train, y_train)
    best_xgb = search.best_estimator_

    tuned_pred = np.clip(best_xgb.predict(X_test), a_min=0, a_max=None)
    evaluate("XGBoost (Tuned)", y_test, tuned_pred)
    return best_xgb


def per_material_evaluation(
    model: Any,
    X_test: pd.DataFrame,
    y_test: pd.Series,
    le_map: dict[str, LabelEncoder],
) -> dict[str, dict[str, float]]:
    preds = np.clip(np.asarray(model.predict(X_test), dtype=float), a_min=0, a_max=None)

    mat_encoder = le_map["Material_Type"]
    mat_codes = X_test["Material_Type"].astype(int).to_numpy()

    material_names: list[str] = []
    for code in mat_codes:
        if 0 <= int(code) < len(mat_encoder.classes_):
            material_names.append(str(mat_encoder.inverse_transform([int(code)])[0]))
        else:
            material_names.append(str(mat_encoder.classes_[0]))

    eval_df = pd.DataFrame(
        {"material": material_names, "actual": np.asarray(y_test, dtype=float), "pred": preds}
    )

    metrics: dict[str, dict[str, float]] = {}
    rows: list[dict[str, float | str]] = []

    for material in MATERIAL_ORDER:
        subset = eval_df[eval_df["material"] == material]
        if subset.empty:
            mae = rmse = mape = 0.0
        else:
            act = subset["actual"].to_numpy()
            prd = subset["pred"].to_numpy()
            mae = float(mean_absolute_error(act, prd))
            rmse = float(np.sqrt(mean_squared_error(act, prd)))
            mape = float(np.mean(np.abs(act - prd) / np.maximum(act, 1.0)) * 100.0)

        rows.append({"Material": material, "MAE": mae, "RMSE": rmse, "MAPE %": mape})
        metrics[material] = {"MAE": mae, "RMSE": rmse, "MAPE": mape}

    metrics_df = pd.DataFrame(rows)
    print("\n[Per Material Metrics]")
    print(metrics_df.to_string(index=False))

    with open(Path(MODEL_DIR) / "per_material_metrics.json", "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2)

    return metrics


def plot_feature_importance(model: Any, feature_names: list[str]) -> None:
    importances = np.asarray(model.feature_importances_, dtype=float)
    fi_df = pd.DataFrame({"feature": feature_names, "importance": importances})
    fi_df = fi_df.sort_values("importance", ascending=False).head(15)

    plt.figure(figsize=(10, 7))
    sns.barplot(data=fi_df, x="importance", y="feature", palette="Blues_d")
    plt.title("POWERGRID Material Demand - Feature Importance (XGBoost)")
    plt.xlabel("Importance")
    plt.ylabel("Feature")
    plt.tight_layout()
    plt.savefig(Path(MODEL_DIR) / "feature_importance.png", dpi=150)
    plt.close()


def save_artifacts(
    model: Any,
    scaler: StandardScaler,
    le_map: dict[str, LabelEncoder],
    feature_names: list[str],
    results_df: pd.DataFrame,
) -> None:
    best_row = results_df.loc[results_df["RMSE"].idxmin()].to_dict()

    metrics_payload = {
        "MAE": float(best_row["MAE"]),
        "RMSE": float(best_row["RMSE"]),
        "R2": float(best_row["R2"]),
        "MAPE": float(best_row["MAPE"]),
        "model_type": "XGBoost (Tuned)" if "XGBoost" in str(best_row["model"]) else str(best_row["model"]),
        "trained_on": DATA_PATH,
        "train_rows": TRAIN_ROWS,
        "test_rows": TEST_ROWS,
        "features": feature_names,
        "trained_at": datetime.now(timezone.utc).isoformat(),
    }

    joblib.dump(model, Path(MODEL_DIR) / "best_model.pkl")
    joblib.dump(scaler, Path(MODEL_DIR) / "scaler.pkl")
    joblib.dump(le_map, Path(MODEL_DIR) / "label_encoders.pkl")
    joblib.dump(feature_names, Path(MODEL_DIR) / "feature_names.pkl")

    with open(Path(MODEL_DIR) / "model_metrics.json", "w", encoding="utf-8") as f:
        json.dump(metrics_payload, f, indent=2)


def main() -> None:
    global DATA_PATH, TRAIN_ROWS, TEST_ROWS

    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default=DATA_PATH, help="Path to powergrid_material_dataset.csv")
    args, _ = parser.parse_known_args()

    DATA_PATH = args.data

    df = load_data(args.data)
    X, y, le_map = preprocess(df)
    feature_names = list(X.columns)

    X_train_raw, X_test_raw, y_train, y_test = time_split(X, y)
    TRAIN_ROWS = len(X_train_raw)
    TEST_ROWS = len(X_test_raw)

    X_train, X_test, le_map = encode_categoricals(X_train_raw, X_test_raw, le_map)

    X_train_sc, X_test_sc, scaler = scale(X_train, X_test)

    results_df, lr, rf, _ = train_models(X_train, X_test, y_train, y_test, X_train_sc, X_test_sc)
    print("\n[Model Comparison]\n", results_df.to_string(index=False))

    best_name = str(results_df.loc[results_df["RMSE"].idxmin(), "model"])
    print(f"\n[Best model] -> {best_name}")

    if "XGBoost" in best_name:
        best_model = tune_xgboost(X_train, y_train, X_test, y_test)
        tuned_metrics = evaluate("XGBoost (Tuned)", y_test, best_model.predict(X_test))
        results_df = pd.concat([results_df, pd.DataFrame([tuned_metrics])], ignore_index=True)
    elif "RandomForest" in best_name:
        print("\n[Tuning] RandomForest RandomizedSearchCV + TimeSeriesSplit")
        search = RandomizedSearchCV(
            RandomForestRegressor(n_jobs=-1, random_state=RANDOM_STATE),
            {
                "n_estimators": randint(200, 600),
                "max_depth": randint(10, 30),
                "min_samples_leaf": randint(2, 10),
            },
            n_iter=20,
            scoring="neg_root_mean_squared_error",
            cv=TimeSeriesSplit(n_splits=5),
            verbose=1,
            random_state=RANDOM_STATE,
            n_jobs=-1,
        )
        search.fit(X_train, y_train)
        best_model = search.best_estimator_
        tuned_metrics = evaluate("RandomForest (Tuned)", y_test, best_model.predict(X_test))
        results_df = pd.concat([results_df, pd.DataFrame([tuned_metrics])], ignore_index=True)
    else:
        best_model = lr

    if hasattr(best_model, "feature_importances_"):
        plot_feature_importance(best_model, feature_names)

    per_material_evaluation(best_model, X_test, y_test, le_map)
    save_artifacts(best_model, scaler, le_map, feature_names, results_df)

    print("\n✅ POWERGRID ML pipeline complete. Artifacts saved to ./models/")


if __name__ == "__main__":
    main()
