import React, { useEffect, useId, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import chatbotApi from "../../api/chatbotApi";
import "./Chatbot.css";

function GroqIcon({ size = 22, className = "" }) {
  const gradientId = useId();
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      aria-hidden="true"
      className={`groq-icon ${className}`}
    >
      <defs>
        <linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#f59e0b" />
          <stop offset="100%" stopColor="#ef4444" />
        </linearGradient>
      </defs>
      <path
        d="M12 2L14.8 9.2L22 12L14.8 14.8L12 22L9.2 14.8L2 12L9.2 9.2L12 2Z"
        fill={`url(#${gradientId})`}
      />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M6 6L18 18M18 6L6 18" stroke="#ffffff" strokeWidth="2.2" strokeLinecap="round" />
    </svg>
  );
}

function SendIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M3 11.5L21 3L12.5 21L10.2 13.8L3 11.5Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
    </svg>
  );
}

function toAssistantHistory(messages) {
  return messages.map((msg) => ({
    role: msg.role === "user" ? "user" : "assistant",
    content: msg.content,
  }));
}

function getChatErrorMessage(err) {
  const status = err?.response?.status;
  if (status === 401) {
    return "ProcBot AI authorization failed. Check Groq API key.";
  }

  if (status === 503 || err?.code === "ERR_NETWORK" || err instanceof TypeError || /network/i.test(err?.message || "")) {
    return "ProcBot AI is temporarily unavailable. Please try again in a moment.";
  }

  if (status && status >= 400) {
    return err?.response?.data?.message || `Failed to send message (${status}). Please try again.`;
  }

  return "Failed to send message. Please check your connection.";
}

const Chatbot = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [hasOpenedOnce, setHasOpenedOnce] = useState(false);
  const [error, setError] = useState(null);
  const [lastFailedMessage, setLastFailedMessage] = useState("");
  const scrollRef = useRef(null);
  const location = useLocation();

  const isLoginPage = location.pathname === "/login" || location.pathname === "/" || location.pathname.startsWith("/login/");

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, isLoading]);

  useEffect(() => {
    if (!error) return undefined;
    const timer = window.setTimeout(() => setError(null), 8000);
    return () => window.clearTimeout(timer);
  }, [error]);

  if (isLoginPage) return null;

  const toggleChat = () => {
    setIsOpen((prev) => !prev);
    if (!hasOpenedOnce) setHasOpenedOnce(true);
    setError(null);
  };

  const requestAssistantResponse = async (messageText, history) => {
    setIsLoading(true);
    setError(null);

    try {
      const response = await chatbotApi.sendMessage(messageText, history);
      const assistantMessage = {
        role: "assistant",
        content: response?.data?.response || "I encountered an issue generating a response. Please try again.",
        suggestions: Array.isArray(response?.data?.suggestions) ? response.data.suggestions : [],
      };
      setMessages((prev) => [...prev, assistantMessage]);
      setError(null);
      setLastFailedMessage("");
    } catch (err) {
      console.error("ProcBot AI request failed:", {
        status: err?.response?.status,
        code: err?.code,
        message: err?.message,
        payload: err?.response?.data,
      });
      setError(getChatErrorMessage(err));
      setLastFailedMessage(messageText);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSendMessage = async (text) => {
    const messageText = (text ?? inputValue).trim();
    if (!messageText || isLoading) return;

    const history = toAssistantHistory(messages);
    setMessages((prev) => [...prev, { role: "user", content: messageText }]);
    setInputValue("");

    await requestAssistantResponse(messageText, history);
  };

  const handleRetry = async () => {
    const retryMessage = lastFailedMessage.trim();
    if (!retryMessage || isLoading) return;

    const latest = messages[messages.length - 1];
    const historySource =
      latest?.role === "user" && latest?.content === retryMessage ? messages.slice(0, -1) : messages;

    await requestAssistantResponse(retryMessage, toAssistantHistory(historySource));
  };

  return (
    <div className={`pg-chatbot-container ${isOpen ? "is-open" : ""}`}>
      <button
        className={`pg-chatbot-fab ${!hasOpenedOnce ? "pulse-animation" : ""}`}
        onClick={toggleChat}
        aria-label={isOpen ? "Close ProcBot AI" : "Open ProcBot AI"}
      >
        {isOpen ? <CloseIcon /> : <GroqIcon size={28} className={isLoading ? "groq-thinking" : ""} />}
      </button>

      <div className="pg-chatbot-panel" role="dialog" aria-label="ProcBot AI chatbot panel">
        <div className="pg-chatbot-header">
          <div className="pg-chatbot-title">
            <GroqIcon size={22} className={isLoading ? "groq-thinking" : ""} />
            <div>
              <h3>ProcBot AI</h3>
              <p>Online · Powered by Groq</p>
            </div>
          </div>
        </div>

        <div className="pg-chatbot-messages pg-scrollbar" ref={scrollRef}>
          {messages.length === 0 && (
            <div className="pg-chat-welcome">
              <GroqIcon size={30} />
              <h4>Welcome to ProcBot AI</h4>
              <p>How can I help with procurement planning, inventory risk, or forecast insights today?</p>
              <div className="pg-suggested-initial">
                <button type="button" onClick={() => handleSendMessage("Which materials are at stockout risk?")}>
                  Stockout risks?
                </button>
                <button type="button" onClick={() => handleSendMessage("Show me the forecast summary")}>
                  Forecast summary
                </button>
              </div>
            </div>
          )}

          {messages.map((msg, idx) => (
            <div key={`${msg.role}-${idx}`} className={`pg-chat-msg-wrapper ${msg.role}`}>
              <div className="pg-chat-msg">{msg.content}</div>
              {msg.suggestions && msg.suggestions.length > 0 ? (
                <div className="pg-chat-suggestions">
                  {msg.suggestions.map((suggestion, sIdx) => (
                    <button
                      key={`${suggestion}-${sIdx}`}
                      type="button"
                      className="pg-suggestion-chip"
                      onClick={() => handleSendMessage(suggestion)}
                    >
                      {suggestion}
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
          ))}

          {isLoading ? (
            <div className="pg-chat-msg-wrapper assistant">
              <div className="pg-chat-msg pg-typing-indicator">
                <span />
                <span />
                <span />
              </div>
            </div>
          ) : null}

          {error ? (
            <div className="pg-chat-error">
              <span>{error}</span>
              {lastFailedMessage ? (
                <button type="button" className="pg-chat-retry-btn" onClick={handleRetry} disabled={isLoading}>
                  Retry
                </button>
              ) : null}
            </div>
          ) : null}
        </div>

        <div className="pg-chatbot-input-area">
          <input
            type="text"
            placeholder="Ask about forecasts, inventory..."
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSendMessage()}
            disabled={isLoading}
            aria-label="Type a message to ProcBot AI"
          />
          <button
            type="button"
            onClick={() => handleSendMessage()}
            disabled={!inputValue.trim() || isLoading}
            className="pg-chat-send-btn"
            aria-label="Send message to ProcBot AI"
          >
            <SendIcon />
          </button>
        </div>
      </div>
    </div>
  );
};

export default Chatbot;
