package com.videogameaholic.intellij.starcoder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.WindowManager;
import com.videogameaholic.intellij.starcoder.settings.StarCoderSettings;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class StarCoderService {

    private int statusCode = 200;

    public String[] getCodeCompletionHints(CharSequence editorContents, int cursorPosition) {
        StarCoderSettings settings = StarCoderSettings.getInstance();
        if(!settings.isSaytEnabled()) return null;

        PromptModel fimModel = settings.getFimTokenModel();
        String starCoderPrompt = fimModel.generateFIMPrompt("",editorContents.toString(),cursorPosition);
        if(starCoderPrompt.isEmpty()) return null;

        HttpPost httpPost = buildApiPost(settings, starCoderPrompt);
        String generatedText = getApiResponse(httpPost);
        return fimModel.buildSuggestionList(generatedText);
    }

    private HttpPost buildApiPost (StarCoderSettings settings, String starCoderPrompt) {
        String apiURL = settings.getApiURL();
        String bearerToken = settings.getApiToken();
        float temperature = settings.getTemperature();
        int maxNewTokens = settings.getMaxNewTokens();
        float topP = settings.getTopP();
        float repetitionPenalty = settings.getRepetitionPenalty();

        HttpPost httpPost = new HttpPost(apiURL);
        if(!bearerToken.isBlank()) httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        JsonObject httpBody = new JsonObject();
        httpBody.addProperty("inputs", starCoderPrompt);

        JsonObject parameters = new JsonObject();
        parameters.addProperty("temperature", temperature);
        parameters.addProperty("max_new_tokens", maxNewTokens);
        parameters.addProperty("top_p", topP);
        parameters.addProperty("repetition_penalty", repetitionPenalty);
        httpBody.add("parameters", parameters);

        StringEntity requestEntity = new StringEntity(httpBody.toString(), ContentType.APPLICATION_JSON);
        httpPost.setEntity(requestEntity);
        return httpPost;
    }

    private String getApiResponse(HttpPost httpPost) {
        String responseText = "";
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpResponse response = httpClient.execute(httpPost);

            // Check the response status code
            int oldStatusCode = statusCode;
            statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != oldStatusCode) {
                // Update the widget based on the new status code
                for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
                    WindowManager.getInstance().getStatusBar(openProject).updateWidget(StarCoderWidget.ID);
                }
            }
            if (statusCode != 200) {
                return responseText;
            }

            Gson gson = new Gson();
            String responseBody = EntityUtils.toString(response.getEntity());
            JsonArray responseArray = gson.fromJson(responseBody, JsonArray.class);
            if(responseArray.size()>0) {
                JsonObject responseObject = responseArray.get(0).getAsJsonObject();
                if(responseObject.get("generated_text")!=null) {
                    responseText = responseObject.get("generated_text").getAsString();
                }
            }

            httpClient.close();

        } catch (IOException e) {
            // TODO log exception
        }
        return responseText;
    }

    public String replacementSuggestion (String prompt) {
        // Default to returning the same text.
        String replacement = prompt;

        StarCoderSettings settings = StarCoderSettings.getInstance();
        HttpPost httpPost = buildApiPost(settings, prompt);
        String generatedText = getApiResponse(httpPost);
        if(!StringUtils.isEmpty(generatedText)) {
            replacement = generatedText;
        }

        return replacement;
    }

    public int getStatus () {
        return statusCode;
    }
}
