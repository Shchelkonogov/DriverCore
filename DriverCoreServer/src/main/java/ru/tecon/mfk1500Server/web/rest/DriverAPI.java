package ru.tecon.mfk1500Server.web.rest;

import ru.tecon.mfk1500Server.web.ejb.DriverAppSB;

import javax.ejb.EJB;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Maksim Shchelkonogov
 */
@Path("/")
public class DriverAPI {

    @EJB
    private DriverAppSB bean;

    @GET
    @Path("/echo")
    public String echo(@QueryParam("q") String original) {
        return original;
    }

    @POST
    @Path("/addDriver")
    public Response addDriver(@Context HttpServletRequest request, @QueryParam("name") String name, @QueryParam("port") int port) {
        if (name == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Not null \"name\" parameter is required").build();
        }
        bean.put(name, request.getRemoteHost() + ":" + port);
        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Path("/getDrivers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDrivers() {
        return Response.ok(bean.toJSON()).build();
    }

    @POST
    @Path("/removeDriver")
    public Response removeDriver(@QueryParam("name") String name) {
        bean.remove(name);

        return Response.ok().build();
    }

    @GET
    @Path("/checkDriver")
    public Response checkDriver(@QueryParam("name") String name) {
        return Response.status(bean.containsKey(name) ? Response.Status.OK : Response.Status.NO_CONTENT).build();
    }
}
