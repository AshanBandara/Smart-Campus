package com.smartcampus.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("discovery")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryResource {

    @GET
    public Response discover() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("api", "Smart Campus Sensor & Room Management API");
        response.put("version", "1.0");
        response.put("contact", "admin@smartcampus.university.ac.uk");
        response.put("description", "RESTful API for managing campus rooms and IoT sensors.");

        Map<String, String> links = new LinkedHashMap<>();
        links.put("rooms", "/api/v1/rooms");
        links.put("sensors", "/api/v1/sensors");
        response.put("resources", links);

        return Response.ok(response).build();
    }
}