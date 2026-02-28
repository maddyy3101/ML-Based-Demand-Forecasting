import axiosClient from "./axiosClient";

export const forecastApi = {
  create: async (payload) => (await axiosClient.post("/forecasts", payload)).data,
  batch: async (payload) => (await axiosClient.post("/forecasts/batch", payload)).data,
  history: async (params) => (await axiosClient.get("/forecasts/history", { params })).data,
  features: async () => (await axiosClient.get("/forecasts/features")).data,
  accuracy: async () => (await axiosClient.get("/forecasts/accuracy")).data,
  explanation: async (id) => (await axiosClient.get(`/forecasts/${id}/explanation`)).data,
  whatIf: async (payload) => (await axiosClient.post("/forecasts/what-if", payload)).data,
  asyncForecast: async (payload) => (await axiosClient.post("/forecasts/async", payload)).data,
  patchActual: async (id, payload) =>
    (await axiosClient.patch(`/forecasts/${id}/actual`, payload)).data,
};
