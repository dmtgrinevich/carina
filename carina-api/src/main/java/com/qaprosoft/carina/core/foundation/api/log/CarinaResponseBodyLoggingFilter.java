package com.qaprosoft.carina.core.foundation.api.log;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.hamcrest.Matcher;

import com.qaprosoft.carina.core.foundation.api.http.ContentTypeEnum;

import io.restassured.builder.ResponseBuilder;
import io.restassured.filter.FilterContext;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.internal.RestAssuredResponseImpl;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

public class CarinaResponseBodyLoggingFilter extends ResponseLoggingFilter {

    private final PrintStream stream;
    private final Matcher<?> matcher;
    private final boolean shouldPrettyPrint;
    private final Set<String> hiddenPaths;
    private final ContentTypeEnum contentType;

    public CarinaResponseBodyLoggingFilter(boolean prettyPrint, PrintStream stream, Matcher<? super Integer> matcher, Set<String> hiddenPaths,
            ContentTypeEnum contentType) {
        Validate.notNull(stream, "Print stream cannot be null");
        Validate.notNull(matcher, "Matcher cannot be null");
        this.shouldPrettyPrint = prettyPrint;
        this.stream = stream;
        this.matcher = matcher;
        this.hiddenPaths = new HashSet<>(hiddenPaths);
        this.contentType = contentType;
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);
        final int statusCode = response.statusCode();
        if (matcher.matches(statusCode)) {
            CarinaBodyPrinter.printResponseBody(response, stream, shouldPrettyPrint, hiddenPaths, contentType);
            final byte[] responseBody;
            responseBody = response.asByteArray();
            response = cloneResponseIfNeeded(response, responseBody);
        }

        return response;
    }

    /*
     * If body expectations are defined we need to return a new Response otherwise the stream
     * has been closed due to the logging.
     */
    private Response cloneResponseIfNeeded(Response response, byte[] responseAsString) {
        if (responseAsString != null && response instanceof RestAssuredResponseImpl && !((RestAssuredResponseImpl) response).getHasExpectations()) {
            final Response build = new ResponseBuilder().clone(response).setBody(responseAsString).build();
            ((RestAssuredResponseImpl) build).setHasExpectations(true);
            return build;
        }
        return response;
    }

}
