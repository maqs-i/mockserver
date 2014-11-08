package org.mockserver.server;

import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.mappers.HttpServletToMockServerRequestMapper;
import org.mockserver.mappers.MockServerToHttpServletResponseMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.filters.LogFilter;
import org.mockserver.streams.IOStreamUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {

    private static final long serialVersionUID = 5058943788293770703L;

    // mockserver
    private LogFilter logFilter = new LogFilter();
    private MockServerMatcher mockServerMatcher = new MockServerMatcher();
    private ActionHandler actionHandler = new ActionHandler(logFilter);
    // mappers
    private HttpServletToMockServerRequestMapper httpServletToMockServerRequestMapper = new HttpServletToMockServerRequestMapper();
    private MockServerToHttpServletResponseMapper mockServerToHttpServletResponseMapper = new MockServerToHttpServletResponseMapper();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();

    public void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    public void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    public void doPut(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String requestPath = retrieveRequestPath(httpServletRequest);
        if (requestPath.equals("/stop")) {
            httpServletResponse.setStatus(HttpStatusCode.NOT_IMPLEMENTED_501.code());
        } else if (requestPath.equals("/dumpToLog")) {
            mockServerMatcher.dumpToLog(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
            httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
        } else if (requestPath.equals("/reset")) {
            logFilter.reset();
            mockServerMatcher.reset();
            httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
        } else if (requestPath.equals("/clear")) {
            HttpRequest httpRequest = httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest));
            logFilter.clear(httpRequest);
            mockServerMatcher.clear(httpRequest);
            httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
        } else if (requestPath.equals("/expectation")) {
            Expectation expectation = expectationSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest));
            mockServerMatcher.when(expectation.getHttpRequest(), expectation.getTimes()).thenRespond(expectation.getHttpResponse(false)).thenForward(expectation.getHttpForward()).thenCallback(expectation.getHttpCallback());
            httpServletResponse.setStatus(HttpStatusCode.CREATED_201.code());
        } else if (requestPath.equals("/retrieve")) {
            Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
            IOStreamUtils.writeToOutputStream(expectationSerializer.serialize(expectations).getBytes(), httpServletResponse);
            httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
        } else if (requestPath.equals("/verify")) {
            String result = logFilter.verify(verificationSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
            if (result.isEmpty()) {
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
            } else {
                IOStreamUtils.writeToOutputStream(result.getBytes(), httpServletResponse);
                httpServletResponse.setStatus(HttpStatusCode.NOT_ACCEPTABLE_406.code());
            }
        } else {
            mockResponse(httpServletRequest, httpServletResponse);
        }
    }

    private String retrieveRequestPath(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getPathInfo() != null && httpServletRequest.getContextPath() != null ? httpServletRequest.getPathInfo() : httpServletRequest.getRequestURI();
    }

    private void mockResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        HttpRequest httpRequest = httpServletToMockServerRequestMapper.mapHttpServletRequestToMockServerRequest(httpServletRequest);
        HttpResponse httpResponse = actionHandler.processAction(mockServerMatcher.handle(httpRequest), httpRequest);
        mapResponse(httpResponse, httpServletResponse);
    }


    private void mapResponse(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse != null) {
            mockServerToHttpServletResponseMapper.mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);
        } else {
            httpServletResponse.setStatus(HttpStatusCode.NOT_FOUND_404.code());
        }
    }
}
