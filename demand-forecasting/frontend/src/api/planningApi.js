import axiosClient from "./axiosClient";

export const planningApi = {
  replenishment: async (payload) =>
    (await axiosClient.post("/planning/replenishment", payload)).data,
  purchasePlan: async (payload) =>
    (await axiosClient.post("/planning/purchase-plan", payload)).data,
  exceptions: async () => (await axiosClient.get("/planning/exceptions")).data,
};
