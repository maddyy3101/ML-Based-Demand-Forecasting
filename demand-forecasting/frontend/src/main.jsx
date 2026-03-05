import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./context/AuthContext";
import UiEffects from "./components/shared/UiEffects";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <UiEffects />
        <App />
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>
);
