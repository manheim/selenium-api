package com.xing.qa.selenium.grid.hub;

import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.web.Hub;
import org.openqa.grid.web.servlet.RegistryBasedServlet;
import org.openqa.selenium.BuildInfo;
import org.openqa.selenium.Capabilities;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Force deregister a node from the hub
 *
 * @author Jason Antman (jason.antman@coxautoinc.com)
 */
public class ForceDeregister extends RegistryBasedServlet {
    static final long serialVersionUID = -1;

    private final Logger log = Logger.getLogger(getClass().getName());
    private String coreVersion;

    public ForceDeregister() {
        this(null);
    }

    public ForceDeregister(GridRegistry registry) {
        super(registry);

        coreVersion = new BuildInfo().getReleaseLabel();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String node_id = req.getParameter("id");
        if(node_id == null || "".equals(node_id)) {
          log.log(Level.WARNING, "Got request with node_id NULL or empty string");
          resp.setContentType("text/plain");
          resp.setCharacterEncoding("UTF-8");
          resp.setStatus(400);
          resp.getWriter().write("ERROR: id parameter must be specified and a valid node ID.");
          resp.getWriter().flush();
          return;
        }
        log.log(Level.INFO, "Got request to force-deregister node with ID: " + node_id);
        GridRegistry registry = getRegistry();
        RemoteProxy proxy = registry.getProxyById(node_id);
        if(proxy == null) {
          log.log(Level.WARNING, "GridRegistry does not contain a node with ID " + node_id + " (null).");
          resp.setContentType("text/plain");
          resp.setCharacterEncoding("UTF-8");
          resp.setStatus(400);
          resp.getWriter().write("ERROR: No node in GridRegistry with ID: " + node_id);
          resp.getWriter().flush();
          return;
        }
        log.log(Level.INFO, "Looked up node in registry; RemoteProxy: ", proxy);
        log.log(Level.INFO, "Force-deregistering node: " + node_id);
        registry.removeIfPresent(proxy);
        resp.setContentType("text/plain");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(200);
        resp.getWriter().write("OK: Force-deregistered node: " + node_id);
        resp.getWriter().flush();
    }
}
