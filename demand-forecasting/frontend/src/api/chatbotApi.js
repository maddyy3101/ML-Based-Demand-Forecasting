import axios from "axios";

// Keep chatbot calls isolated from auth interceptors so expired/stale
// dashboard tokens do not break ProcBot on a public backend endpoint.
const chatbotClient = axios.create({
  baseURL: "/api/v1/chatbot",
  timeout: 45000,
});

const chatbotApi = {
  sendMessage: (message, history) => {
    return chatbotClient.post("/message", { message, history });
  },
};

export default chatbotApi;
