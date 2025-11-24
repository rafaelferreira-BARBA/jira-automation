package org.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class JiraClient {
    private final String domain;
    private final String email;
    private final String token;
    private final String projectKey;
    private final HttpClient httpClient;

    public JiraClient(String domain, String email, String token, String projectKey) {
        this.domain = domain;
        this.email = email;
        this.token = token;
        this.projectKey = projectKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void createStory(String summary, String description, String component) {
        try {
            String json = String.format("""
            {
              "fields": {
                "project": { "key": "%s" },
                "summary": "%s",
                "description": "%s",
                "issuetype": { "name": "User Story" },
                "components": [{ "name": "%s" }]
              }
            }
            """, projectKey, escape(summary), escape(description), escape(component));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + domain + "/rest/api/2/issue"))
                    .header("Authorization", "Basic " + base64(email + ":" + token))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                System.err.println("Erro ao criar U.S: " + response.body());
            } else {
                System.out.println("Criada: " + summary + " " + response.body());
            }

        } catch (Exception e) {
            System.err.println("Erro ao criar história: " + e.getMessage());
        }
    }

    public void createSubTask(
            String parentKey,
            String summary,
            String description,
            String component,
            String label,
            int sp
    ) {
        try {
            String json = String.format("""
        {
          "fields": {
            "project": { "key": "%s" },
            "summary": "%s",
            "description": "%s",
            "issuetype": { "name": "Sub-task" },
            "parent": { "key": "%s" },
            "components": [{ "name": "%s" }],
            "labels": ["%s"],
            "customfield_19616": %s,
            "customfield_10006": %s
          }
        }
        """,
                    projectKey,
                    escape(summary),
                    escape(description),
                    escape(parentKey),
                    escape(component),
                    escape(label),
                    sp,
                    sp
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + domain + "/rest/api/2/issue"))
                    .header("Authorization", "Basic " + base64(email + ":" + token))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                System.err.println("Erro ao criar Sub-task: " + response.body());
            } else {
                System.out.println("Sub-task criada: " + summary + " " + response.body());
            }

        } catch (Exception e) {
            System.err.println("Erro ao criar sub-task: " + e.getMessage());
        }
    }

    private String base64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes());
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("\"", "\\\"");
    }
}
