import axiosClient from "./axiosClient";

export const metricsApi = {
  performance: async (params) =>
    (await axiosClient.get("/metrics/performance", { params })).data,
  drift: async () => (await axiosClient.get("/metrics/drift")).data,
};
