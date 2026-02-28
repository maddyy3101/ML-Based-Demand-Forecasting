import axiosClient from "./axiosClient";

export const jobsApi = {
  list: async () => (await axiosClient.get("/jobs")).data,
  status: async (id) => (await axiosClient.get(`/jobs/${id}`)).data,
};
