package com.example.soar;

import kong.unirest.GetRequest;
import kong.unirest.HttpResponse;
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

        kong.unirest.HttpRequestWithBody postRequest = mock(kong.unirest.HttpRequestWithBody.class);
        when(postRequest.asString()).thenReturn(response);
        unirestMock.when(() -> Unirest.post(anyString())).thenReturn(postRequest);

        String result = workers.freezeAccount("testuser");
        assertThat(result).isEqualTo("FROZEN");
    }
}
