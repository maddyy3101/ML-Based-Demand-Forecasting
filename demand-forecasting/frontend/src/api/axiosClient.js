import axios from "axios";

const axiosClient = axios.create({
  baseURL: "/api/v1",
  timeout: 45000,
});

axiosClient.interceptors.request.use((config) => {
  const token = localStorage.getItem("pg_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default axiosClient;
