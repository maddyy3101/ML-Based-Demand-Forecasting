import axiosClient from "./axiosClient";

export const adminApi = {
  uploadDataset: async (file, onUploadProgress) => {
    const form = new FormData();
    form.append("file", file);
    const response = await axiosClient.post("/admin/upload-dataset", form, {
      headers: { "Content-Type": "multipart/form-data" },
      onUploadProgress,
    });
    return response.data;
  },
  retrainingStatus: async (id) =>
    (await axiosClient.get(`/admin/retraining-status/${id}`)).data,
  users: async () => (await axiosClient.get("/admin/users")).data,
  createUser: async (payload) => (await axiosClient.post("/admin/users", payload)).data,
  updateUser: async (id, payload) =>
    (await axiosClient.put(`/admin/users/${id}`, payload)).data,
  deactivateUser: async (id) => (await axiosClient.delete(`/admin/users/${id}`)).data,
  reactivateUser: async (id) => (await axiosClient.post(`/admin/users/${id}/reactivate`)).data,
  deleteUserPermanently: async (id) =>
    (await axiosClient.delete(`/admin/users/${id}/permanent`)).data,
  systemHealth: async () => (await axiosClient.get("/admin/system-health")).data,
  auditLog: async (params) => (await axiosClient.get("/admin/audit-log", { params })).data,
  procDocStatus: async () => (await axiosClient.get("/admin/procdoc/status")).data,
  authenticateProcDocKey: async (payload) =>
    (await axiosClient.post("/admin/procdoc/authenticate", payload)).data,
};
