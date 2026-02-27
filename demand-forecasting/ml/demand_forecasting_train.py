"""
Demand Forecasting - ML Training Pipeline
==========================================
Dataset  : sales_data.csv (~76k rows)
Target   : Demand
Models   : Linear Regression, Random Forest, XGBoost
"""

import warnings
warnings.filterwarnings("ignore")

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

from sklearn.linear_model import LinearRegression
from sklearn.ensemble import RandomForestRegressor
from sklearn.preprocessing import LabelEncoder, StandardScaler
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

from xgboost import XGBRegressor
from scipy.stats import randint, uniform

import joblib
import os

DATA_PATH    = "sales_data.csv"
MODEL_DIR    = "models"
RANDOM_STATE = 42
os.makedirs(MODEL_DIR, exist_ok=True)


def load_data(path: str) -> pd.DataFrame:
    df = pd.read_csv(path)
    print(f"[load] Shape: {df.shape}")
    print(df.dtypes)
    print(df.isnull().sum())
    return df


def preprocess(df: pd.DataFrame):
    df = df.copy()

    df["Date"] = pd.to_datetime(df["Date"])
    df["year"]      = df["Date"].dt.year
    df["month"]     = df["Date"].dt.month
    df["day"]       = df["Date"].dt.day
    df["week"]      = df["Date"].dt.isocalendar().week.astype(int)
    df["dayofweek"] = df["Date"].dt.dayofweek
    df["quarter"]   = df["Date"].dt.quarter
    df.drop(columns=["Date"], inplace=True)

    df.drop(columns=["Store ID", "Product ID"], inplace=True)

    cat_cols = ["Category", "Region", "Weather Condition", "Seasonality"]
    le_map: dict = {}
    for col in cat_cols:
        le = LabelEncoder()
        df[col] = le.fit_transform(df[col].astype(str))
        le_map[col] = le

    TARGET = "Demand"
    X = df.drop(columns=[TARGET])
    y = df[TARGET]

    print(f"[preprocess] Features: {list(X.columns)}")
    return X, y, le_map


def time_split(X: pd.DataFrame, y: pd.Series, test_ratio: float = 0.2):
    n = len(X)
    split_idx = int(n * (1 - test_ratio))
    X_train, X_test = X.iloc[:split_idx], X.iloc[split_idx:]
    y_train, y_test = y.iloc[:split_idx], y.iloc[split_idx:]
    print(f"[split] Train: {len(X_train)} | Test: {len(X_test)}")
    return X_train, X_test, y_train, y_test


def scale(X_train, X_test):
    scaler = StandardScaler()
    X_train_sc = scaler.fit_transform(X_train)
    X_test_sc  = scaler.transform(X_test)
    return X_train_sc, X_test_sc, scaler


def evaluate(name: str, y_true, y_pred) -> dict:
    mae  = mean_absolute_error(y_true, y_pred)
    rmse = np.sqrt(mean_squared_error(y_true, y_pred))
    r2   = r2_score(y_true, y_pred)
    print(f"\n{'─'*40}")
    print(f"  Model : {name}")
    print(f"  MAE   : {mae:.4f}")
    print(f"  RMSE  : {rmse:.4f}")
    print(f"  R²    : {r2:.4f}")
    return {"model": name, "MAE": mae, "RMSE": rmse, "R2": r2}


def train_models(X_train, X_test, y_train, y_test, X_train_sc, X_test_sc):
    results = []

    lr = LinearRegression()
    lr.fit(X_train_sc, y_train)
    results.append(evaluate("LinearRegression", y_test, lr.predict(X_test_sc)))

    rf = RandomForestRegressor(n_estimators=200, max_depth=15,
                               min_samples_leaf=5, n_jobs=-1, random_state=RANDOM_STATE)
    rf.fit(X_train, y_train)
    results.append(evaluate("RandomForest", y_test, rf.predict(X_test)))

    xgb = XGBRegressor(n_estimators=300, learning_rate=0.05, max_depth=7,
                       subsample=0.8, colsample_bytree=0.8, tree_method="hist",
                       random_state=RANDOM_STATE, n_jobs=-1)
    xgb.fit(X_train, y_train, eval_set=[(X_test, y_test)], verbose=False)
    results.append(evaluate("XGBoost", y_test, xgb.predict(X_test)))

    return pd.DataFrame(results), lr, rf, xgb


def pick_best(results_df: pd.DataFrame) -> str:
    best_row = results_df.loc[results_df["RMSE"].idxmin()]
    print(f"\n[best model] → {best_row['model']}  (RMSE={best_row['RMSE']:.4f})")
    return best_row["model"]


def tune_xgboost(X_train, y_train, X_test, y_test):
    from sklearn.model_selection import RandomizedSearchCV, TimeSeriesSplit
    param_dist = {
        "n_estimators":     randint(200, 600),
        "max_depth":        randint(4, 10),
        "learning_rate":    uniform(0.01, 0.2),
        "subsample":        uniform(0.6, 0.4),
        "colsample_bytree": uniform(0.5, 0.5),
        "min_child_weight": randint(1, 10),
        "gamma":            uniform(0, 0.5),
    }
    tscv = TimeSeriesSplit(n_splits=5)
    search = RandomizedSearchCV(
        XGBRegressor(tree_method="hist", random_state=RANDOM_STATE, n_jobs=-1),
        param_distributions=param_dist, n_iter=30,
        scoring="neg_root_mean_squared_error",
        cv=tscv, verbose=1, random_state=RANDOM_STATE, n_jobs=-1
    )
    search.fit(X_train, y_train)
    best_xgb = search.best_estimator_
    print(f"\n[tuning] Best params: {search.best_params_}")
    evaluate("XGBoost (Tuned)", y_test, best_xgb.predict(X_test))
    return best_xgb


def plot_feature_importance(model, feature_names: list, title: str = "Feature Importance"):
    importances = model.feature_importances_
    fi_df = pd.DataFrame({"feature": feature_names, "importance": importances})
    fi_df = fi_df.sort_values("importance", ascending=False).head(15)
    plt.figure(figsize=(10, 6))
    sns.barplot(data=fi_df, x="importance", y="feature", palette="viridis")
    plt.title(title)
    plt.tight_layout()
    save_path = os.path.join(MODEL_DIR, "feature_importance.png")
    plt.savefig(save_path, dpi=150)
    plt.close()
    print(f"[feature importance] Saved → {save_path}")
    print(fi_df.to_string(index=False))


def save_artifacts(model, scaler, le_map: dict, feature_names: list):
    joblib.dump(model,         os.path.join(MODEL_DIR, "best_model.pkl"))
    joblib.dump(scaler,        os.path.join(MODEL_DIR, "scaler.pkl"))
    joblib.dump(le_map,        os.path.join(MODEL_DIR, "label_encoders.pkl"))
    joblib.dump(feature_names, os.path.join(MODEL_DIR, "feature_names.pkl"))
    print(f"\n[save] All artifacts saved to ./{MODEL_DIR}/")


def main():
    df = load_data(DATA_PATH)
    X, y, le_map = preprocess(df)
    feature_names = list(X.columns)
    X_train, X_test, y_train, y_test = time_split(X, y)
    X_train_sc, X_test_sc, scaler = scale(X_train, X_test)
    results_df, lr, rf, xgb = train_models(
        X_train, X_test, y_train, y_test, X_train_sc, X_test_sc)
    print("\n[comparison table]")
    print(results_df.to_string(index=False))
    best_name = pick_best(results_df)

    if "XGBoost" in best_name:
        print("\n[tuning XGBoost]...")
        best_model = tune_xgboost(X_train, y_train, X_test, y_test)
    elif "RandomForest" in best_name:
        from sklearn.model_selection import RandomizedSearchCV, TimeSeriesSplit
        param_dist = {
            "n_estimators":     randint(100, 500),
            "max_depth":        randint(5, 30),
            "min_samples_leaf": randint(2, 20),
        }
        search = RandomizedSearchCV(
            RandomForestRegressor(n_jobs=-1, random_state=RANDOM_STATE),
            param_distributions=param_dist, n_iter=15,
            scoring="neg_root_mean_squared_error",
            cv=TimeSeriesSplit(n_splits=5), verbose=1, random_state=RANDOM_STATE
        )
        search.fit(X_train, y_train)
        best_model = search.best_estimator_
        evaluate("RandomForest (Tuned)", y_test, best_model.predict(X_test))
    else:
        best_model = lr

    if hasattr(best_model, "feature_importances_"):
        plot_feature_importance(best_model, feature_names,
                                title=f"{best_name} — Feature Importance")

    save_artifacts(best_model, scaler, le_map, feature_names)
    print("\n✅ Pipeline complete.")


if __name__ == "__main__":
    main()
