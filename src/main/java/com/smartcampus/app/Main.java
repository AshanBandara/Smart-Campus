package com.smartcampus.app;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    // Base URI now includes the /api/v1 prefix directly
    public static final String BASE_URI = "http://0.0.0.0:8080/api/v1/";

    public static HttpServer startServer() {
        ResourceConfig rc = new ResourceConfig();
        rc.register(com.smartcampus.resource.DiscoveryResource.class);
        rc.register(com.smartcampus.resource.RoomResource.class);
        rc.register(com.smartcampus.resource.SensorResource.class);
        rc.register(com.smartcampus.exception.RoomNotEmptyExceptionMapper.class);
        rc.register(com.smartcampus.exception.LinkedResourceNotFoundExceptionMapper.class);
        rc.register(com.smartcampus.exception.SensorUnavailableExceptionMapper.class);
        rc.register(com.smartcampus.exception.GlobalExceptionMapper.class);
        rc.register(com.smartcampus.filter.LoggingFilter.class);
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
    }

    public static void main(String[] args) throws IOException {
        final HttpServer server = startServer();
        LOGGER.info("Smart Campus API started at http://localhost:8080/api/v1/");
        LOGGER.info("Press ENTER to stop the server...");
        System.in.read();
        server.stop();
    }
}