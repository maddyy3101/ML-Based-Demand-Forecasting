import axiosClient from "./axiosClient";

export const authApi = {
  login: async (payload) => (await axiosClient.post("/auth/login", payload)).data,
  register: async (payload) => (await axiosClient.post("/auth/register", payload)).data,
  me: async () => (await axiosClient.get("/auth/me")).data,
};
