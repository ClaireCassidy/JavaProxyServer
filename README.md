# JavaProxyServer
*Multithreaded Proxy Server implemented in Java, communicating with remote endpoints via HTTP/HTTPS via socket connections and implementing efficient persistent caching.*

See documentation.pdf for detailed discussion

A Web Proxy Server is a server that acts as a middleman between a client and a real third-party server. The web proxy intercepts all outgoing HTTP(S) and WebSocket requests from the client, and fulfils those requests on the client’s behalf.  This can either be achieved by contacting the third-party server directly to obtain the required resource(s), or by delivering an in-date  cached copy of the  resource that  has been obtained by the server on a  recent request for the same resource.  In doing so, the proxy server can reduce response time for the client and bandwidth on the local network by negating the need to establish a connection with the third-party server and transfer the requested resources from a potentially distant source.  This can result in increased performance of the network overall.  Furthermore, the proxy can also be configured to dynamically block resources,  including whole websites or specific resources on those websites,  making the proxy a more general tool for a network administrator to manage network access.

This proxy sever implementation in Java can be easily configured to work with any browser by following the steps in the documentation.

This implementation covers the following functionalities:
- Respond  to  HTTP  and  HTTPs  requests  and  display  each  request  on  a  management console.  It should forward the request to the web server and relay the response in the browser.
- Dynamically block selected URLs via the management console
- Efficiently  cache  HTTP  requests  locally  and  thus  save  bandwidth.  A demonstration of this providing timing and bandwidth measurements is available in the documentation.
