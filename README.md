# Welcome to the Meshcaline Proxy project!

This project provides a HTTP proxy implementation that support providers of webservice API as well as application 
developers with generic backend-for-frontend capabilities.

It aims to help transforming the programmable web into a composable web.

For more background related to the idea of behind the Meshcaline Proxy, please check out the 
project page at [proxy.meshcaline.org](https://proxy.meshcaline.org)

### Status
Be warned!!! The project is in very early state. For now it is little more than a (partially) working prototype 
/ proof of concept. 
I hope it is still interesting enough to provide [feedback](mailto:meshcalero@meshcaline.org), but definitively 
far from ready for production. 

### Usage

If you want to test the Meshcaline proxy, clone the project and launch it with 

```gradlew bootRun```

The default configuration launches the proxy on port 8080 and proxies all incoming requests that map to the regular expression
`/jsonplaceholder/(.*)` to [https://jsonplaceholder.typicode.com/$1](https://jsonplaceholder.typicode.com/)`. 

A good way to test the proxy behaviour is using [Postman](https://wwww.postman.com/) and send a request to the service
that contains a HTTP header `X-MESHCALINE-QUERY` containing the graphQL query you want to test. 

To get started you might want try out
```
GET http://localhost:8080/jsonplaceholder/users/
```
with `X-MESHCALINE-QUERY` set to
```
query users @GET(fragment:"user", url: "./${id}") 
    { id } 
fragment user on user 
    { id,email } 
```

You have to disable Postman's default header `Accept-Encoding` as the proxy doesn't yet support 
the [Brotli](https://github.com/google/brotli) encoding.

The current implementation deviates from intended behaviour described on the project page in various ways:
* Contrary to the response type described on the project page, the current implementation returns the 
individual resource as a multipart/mixed content type. Proper content negotiation capabilities that also support
streaming json arrays will be added at a later stage of the development.
* While the various parts are streamed independently, the proxy doesn't yet support streaming processiong 
of the body of the individual parts
* The extension of the filtered response with the hypertext controls for the subrequests is still missing.

### Testing with other API
If you want to test Meshcaline Proxy with other API, you have to configure additional proxy mappings.
You can do this permanently by adding additional mappings to property `proxy.config.mappings` in 
the project's `application.yml`. 

Further you can temporarily add additional mappings via the proxy's admin API. 
This allows you add a new mapping by posting a mapping configuration to `/admin/mappings`.
The request body contains a JSON object with two attributes 

```
{ 
    "ingressURIRegEx" : "http://localhost:8080/jsonplaceholder/(.*)", 
    "egressURIReplace" : "https://jsonplaceholder.typicode.com/$1" 
}
```

Temporary mappings will be lost on restart.