package com.example.soar;

import kong.unirest.GetRequest;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.RequestBodyEntity;
import kong.unirest.Unirest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TaskWorkerTest {

    private TaskWorkers workers;
    private MockedStatic<Unirest> unirestMock;

    @BeforeEach
    public void setup() {
        workers = new TaskWorkers();
        unirestMock = mockStatic(Unirest.class);
    }

    @AfterEach
    public void tearDown() {
        unirestMock.close();
    }

    @Test
    public void testGetAccountInfoSuccess() {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.isSuccess()).thenReturn(true);
        when(response.getBody()).thenReturn("{\"username\": \"testuser\"}");

        GetRequest getRequest = mock(GetRequest.class);
        when(getRequest.asString()).thenReturn(response);
        unirestMock.when(() -> Unirest.get(anyString())).thenReturn(getRequest);

        String result = workers.getAccountInfo("testuser");
        assertThat(result).contains("testuser");
    }

    @Test
    public void testFreezeAccountSuccess() {
        HttpResponse response = mock(HttpResponse.class);
        when(response.getStatus()).thenReturn(204);

        HttpRequestWithBody postRequest = mock(HttpRequestWithBody.class);
        when(postRequest.asString()).thenReturn(response);
        unirestMock.when(() -> Unirest.post(anyString())).thenReturn(postRequest);

        String result = workers.freezeAccount("testuser");
        assertThat(result).isEqualTo("SUCCESS");
    }

    @Test
    public void testCheckGlobalFailure() {
        // Runs it many times to ensure we see both Pass and Fail
        int pass = 0, fail = 0;
        for (int i = 0; i < 100; i++) {
            String result = workers.checkGlobalFailure();
            if (result.equals("PASS")) pass++;
            else fail++;
        }
        assertThat(pass).isGreaterThan(0);
        assertThat(fail).isGreaterThan(0);
    }

    @Test
    public void testFindOpenCaseSuccess() {
        HttpResponse<JsonNode> response = mock(HttpResponse.class);
        JsonNode jsonNode = new JsonNode("[{\"id\": \"CASE-123\", \"status\": \"OPEN\"}]");
        
        when(response.isSuccess()).thenReturn(true);
        when(response.getBody()).thenReturn(jsonNode);

        GetRequest getRequest = mock(GetRequest.class);
        when(getRequest.queryString(anyString(), anyString())).thenReturn(getRequest);
        when(getRequest.asJson()).thenReturn(response);
        unirestMock.when(() -> Unirest.get(anyString())).thenReturn(getRequest);

        String result = workers.findOpenCase("testuser");
        assertThat(result).isEqualTo("CASE-123");
    }

    @Test
    public void testAddCaseNoteSuccess() {
        HttpResponse response = mock(HttpResponse.class);
        when(response.getStatus()).thenReturn(200);

        HttpRequestWithBody postRequest = mock(HttpRequestWithBody.class);
        RequestBodyEntity bodyEntity = mock(RequestBodyEntity.class);
        
        when(postRequest.header(anyString(), anyString())).thenReturn(postRequest);
        when(postRequest.body(anyString())).thenReturn(bodyEntity);
        when(bodyEntity.asString()).thenReturn(response);
        
        unirestMock.when(() -> Unirest.post(anyString())).thenReturn(postRequest);

        String result = workers.addCaseNote("CASE-123", "Test Note");
        assertThat(result).isEqualTo("OK");
    }
}
