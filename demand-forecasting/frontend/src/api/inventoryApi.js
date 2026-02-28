import axiosClient from "./axiosClient";

export const inventoryApi = {
  items: async () => (await axiosClient.get("/inventory/items")).data,
  movement: async (payload) => (await axiosClient.post("/inventory/movement", payload)).data,
  movements: async () => (await axiosClient.get("/inventory/movements")).data,
  recommendations: async () =>
    (await axiosClient.get("/inventory/recommendations")).data,
  recommendationActions: async () =>
    (await axiosClient.get("/inventory/recommendations/actions")).data,
  raiseRecommendation: async (inventoryId, payload) =>
    (await axiosClient.post(`/inventory/recommendations/${inventoryId}/raise`, payload || {})).data,
  dismissRecommendation: async (inventoryId, payload) =>
    (await axiosClient.post(`/inventory/recommendations/${inventoryId}/dismiss`, payload || {})).data,
};
