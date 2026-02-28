import axiosClient from "./axiosClient";

export const modelApi = {
  active: async () => (await axiosClient.get("/models/active")).data,
  activate: async (version, payload) =>
    (await axiosClient.post(`/models/${version}/activate`, payload)).data,
};
