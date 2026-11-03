package com.worknest.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpTimeoutException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONException;
import org.json.JSONObject;

import com.worknest.util.GeminiProjectDraftService;
import com.worknest.util.GeminiProjectDraftService.DraftException;

/** Authenticated client endpoint for turning a summary into a project form draft. */
public class AiProjectDraftServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int MAX_BODY_CHARS = 8192;
    private static final int MIN_SUMMARY_CHARS = 20;
    private static final int MAX_SUMMARY_CHARS = 2000;
    private static final long MIN_REQUEST_INTERVAL_MS = 2500L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        prepareResponse(response);

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Please sign in again to draft project details.");
            return;
        }
        if (!"CLIENT".equals(session.getAttribute("role"))) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Only clients can draft project details.");
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (session) {
            Object previousValue = session.getAttribute("lastAiProjectDraftAt");
            if (previousValue instanceof Long
                    && now - (Long) previousValue < MIN_REQUEST_INTERVAL_MS) {
                writeError(response, 429,
                        "Please wait a moment before requesting another draft.");
                return;
            }
            session.setAttribute("lastAiProjectDraftAt", now);
        }

        final JSONObject body;
        try {
            body = readJson(request);
        } catch (JSONException exception) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Enter a short project summary and try again.");
            return;
        } catch (BodyTooLargeException exception) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "The request is too large. Keep the summary to 2,000 characters or fewer.");
            return;
        }

        String summary = body.optString("summary", "").trim();
        if (summary.length() < MIN_SUMMARY_CHARS
                || summary.length() > MAX_SUMMARY_CHARS) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Write a summary between 20 and 2,000 characters.");
            return;
        }

        try {
            JSONObject result = GeminiProjectDraftService.createDraft(summary);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(result.toString());
        } catch (DraftException exception) {
            getServletContext().log("Gemini project draft request could not be completed.");
            int status = exception.getMessage() != null
                    && exception.getMessage().contains("not configured")
                    ? HttpServletResponse.SC_SERVICE_UNAVAILABLE
                    : HttpServletResponse.SC_BAD_GATEWAY;
            writeError(response, status, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "AI drafting was interrupted. Please try again.");
        } catch (HttpTimeoutException exception) {
            getServletContext().log("Gemini project draft request timed out.");
            writeError(response, HttpServletResponse.SC_GATEWAY_TIMEOUT,
                    "The AI request took too long. Please try again, or complete the form manually.");
        } catch (IOException exception) {
            getServletContext().log("Gemini project draft request failed: "
                    + exception.getClass().getSimpleName());
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "The AI service is temporarily unavailable. You can still complete the form manually.");
        }
    }

    private JSONObject readJson(HttpServletRequest request)
            throws IOException, JSONException, BodyTooLargeException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                request.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (body.length() + count > MAX_BODY_CHARS) {
                    throw new BodyTooLargeException();
                }
                body.append(buffer, 0, count);
            }
        }
        return new JSONObject(body.toString());
    }

    private void prepareResponse(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.getWriter().write(new JSONObject()
                .put("success", false)
                .put("error", message)
                .toString());
    }

    private static final class BodyTooLargeException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
