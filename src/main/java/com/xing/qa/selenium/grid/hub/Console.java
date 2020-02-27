package com.xing.qa.selenium.grid.hub;

import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.web.Hub;
import org.openqa.grid.web.servlet.RegistryBasedServlet;
import org.openqa.selenium.BuildInfo;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Console information and more as JSON
 *
 * @author Jens Hausherr (jens.hausherr@xing.com)
 */
public class Console extends RegistryBasedServlet {
    static final long serialVersionUID = -1;

    private final Logger log = Logger.getLogger(getClass().getName());
    private String coreVersion;

    public Console() {
        this(null);
    }

    public Console(GridRegistry registry) {
        super(registry);

        coreVersion = new BuildInfo().getReleaseLabel();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            if ("/requests".equals(req.getPathInfo())) {
                sendJson(pendingRequests(), req, resp);
            } else if ("/simple".equals(req.getPathInfo())) {
                sendJson(simpleStatus(), req, resp);
            } else {
                sendJson(status(), req, resp);
            }
        } catch (JSONException je) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.setStatus(500);
            JSONObject error = new JSONObject();

            try {
                error.put("message", je.getMessage());
                error.put("location", je.getStackTrace());
                error.write(resp.getWriter());
            } catch (JSONException e1) {
              log.log(Level.WARNING, "Failed to write error response", e1);
            }

        }

    }

    protected void sendJson(JSONObject jo, HttpServletRequest req, HttpServletResponse resp) {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(200);
        Writer w;
        try {
            w = resp.getWriter();
            jo.write(w);
        } catch (IOException e) {
            log.log(Level.WARNING, "Error writing response", e);
        } catch (JSONException e) {
            log.log(Level.WARNING, "Failed to serialize JSON response", e);
        }
    }

    protected JSONObject pendingRequests() throws JSONException {
        JSONObject pending = new JSONObject();
        int p = getRegistry().getNewSessionRequestCount();
        List<Map<String,?>> desired;

        if (p > 0) {
            desired = new ArrayList<>();
            for (Capabilities c: getRegistry().getDesiredCapabilities()) {
                desired.add(c.asMap());
            }
        } else {
            desired = Collections.emptyList();
        }

        pending.put("pending", p);
        pending.put("requested_capabilities", desired);

        return pending;
    }

    protected JSONObject status()
        throws JSONException {
        JSONObject status = new JSONObject();

        Hub h = getRegistry().getHub();

        List<JSONObject> nodes = new ArrayList<>();

        for (RemoteProxy proxy : getRegistry().getAllProxies()) {
            try {
                JSONRenderer beta = new WebProxyJsonRenderer(proxy);
                nodes.add(beta.render());
            } catch (Exception e) {}
        }

        status.put("version", coreVersion);
        status.put("configuration", getRegistry().getHub().getConfiguration().toJson().entrySet());
        status.put("host", h.getConfiguration().host);
        status.put("port", h.getConfiguration().port);
        status.put("registration_url", h.getRegistrationURL());
        status.put("nodes", nodes);
        status.put("requests", pendingRequests());

        return status;
    }

    protected JSONObject simpleStatus() throws JSONException {
        JSONObject status = new JSONObject();

        Hub h = getRegistry().getHub();

        Map<String, JSONObject> nodes = new HashMap<String, JSONObject>();
        AutoIncrementMap inUse = new AutoIncrementMap();
        AutoIncrementMap notInUse = new AutoIncrementMap();
        AutoIncrementMap pending = new AutoIncrementMap();

        for (RemoteProxy proxy : getRegistry().getAllProxies()) {
            for (TestSlot slot : proxy.getTestSlots()) {
                String browserName = slot.getCapabilities().get(CapabilityType.BROWSER_NAME).toString().replaceAll("\\s+", "");
                TestSession session = slot.getSession();
                if (session != null) {
                    inUse.increment(browserName);
                } else {
                    notInUse.increment(browserName);
                }
            }
        }

        if (getRegistry().getNewSessionRequestCount() > 0) {
            for (Capabilities c: getRegistry().getDesiredCapabilities()) {
                pending.increment(c.getBrowserName());
            }
        }

        status.put("browsers_in_use", inUse.getAsJsonObject());
        status.put("browsers_free", notInUse.getAsJsonObject());
        status.put("pending_requests", pending.getAsJsonObject());
        return status;
    }
}


class AutoIncrementMap {
  private Map<String, Integer> map = new HashMap<String, Integer>();

  public void increment(String k) {
      if (!map.containsKey(k)) {
        map.put(k, 1);
      } else {
        map.put(k, map.get(k) + 1);
      }
  }

  public int get(String k) {
      return map.containsKey(k) ? map.get(k) : 0;
  }

  public JSONObject getAsJsonObject() throws JSONException {
      JSONObject val = new JSONObject();
      for (String k: map.keySet()) {
        val.put(k, map.get(k));
      }
      return val;
  }
}
