---
layout: default
title: Overview
---

# Welcome to Meshcaline Proxy!

Meshcaline Proxy's mission is to enable the transformation from the 
programmable web to a composable web. 

It addresses two audiences: API providers and Application developers.

The main goal of the Meshcaline Proxy is to provide the advantages of backend-to-frontend [backend-for-frontend (BFF) services](https://samnewman.io/patterns/architectural/bff/) without inheriting
their downsides (see [Why Meshcaline Proxy](why.md)).

Meshcaline Proxy does this by providing a generic HTTP proxy implementation that enables a universal BFF implementation 
via an infrastructure component that is agnostic to the specific webservice API that serve client applications.

The key characteristics of the Meshcaline Proxy are:
* It operates on plain HTTP semantics
* Any existing HTTP WebService API can be used with Meshcaline Proxy, without any adjustments.
* Client requests only require an optional extension to use Meshcaline Proxy's capabilities; when 
  missing, the original request is proxied transparently.
* It leverages GraphQL's query language to let client applications filter the size of responses down to their needs
* It extends GraphQL's query language to enable dynamic composition of arbitrary API calls into a single response,
  specific to the needs of the calling client application
* Special treatment for webservice API implementations that follow the
  [Meshcaline design principles](https://meshcaline.org) significantly simplifies the 
  composition for client applications
* It leverages a streaming architecture, optimized for minimal latency and supporting 
  reactive client application implementations 

You can find the proxy on [Github](https://github.com/meshcalero/meshcaline-proxy)

### Example Scenarios

Let's use a few hypothetical, but hopefully realistic scenarios to describe how Meshcaline Proxy works.

#### Scenario 1: Corporate App

Let's assume a large corporate has built an app that allows their employees to manage all their administrative 
duties with the company. The app leverages various company microservices including an employee search microservice and 
an employee details microservice. A recently launched new service does allow to manage contractual details 
for the new company bike offering.
The bike offering team has convinced the App team that managers need to see their employee's bike-contract, and the
App team would love to add the corresponding feature.
Since the company is using (for for many valid reasons) a BFF architecture, the App doesn't directly access 
the various corporate microservices. Instead all request go through a BFF service managed by a 3rd team. 
So the App team asks the BFF team to provide a new endpoint that encapsulates the following functionality:

1. search for all employees of a given manager via the employee search service
1. retrieve the details for all those employees from the employee details service
1. take the bike-contract number from the employee record and retrieve the contract details from the bike-contract service 
2. Return the list of contract details along with a subset of employee details required for displaying the results in the app.

Unfortunately the team owning with BFF service is a bottleneck for the app development and is overwhelmed 
with feature requests. Since many of those are considered more important than supporting bike contracts, the request
gets on the backlog and it is unlikely that it will be prioritized in the next few quarters.

The App team considers bypassing the BFF service, but this would not only violate some fundamental design, it would
also introduce various client server round-trips. Those would result in high latency and a non-responsive application 
(avoiding this latency is one of the main reasons for the BFF architecture). Consequently app team decided to not
go down that route and wait for a slot on the BFF team's roadmap. 

How would Meshcaline Proxy help in that scenario?

By leveraging Meshcaline Proxy's query extensions, the app team would have been able to specify their feature request as a 
dynamic request to Meshcaline Proxy.

Let's assume the three microservice endpoints that have to be composed for the feature are
* https://search.corporate.com/emplyees?manager=${manager_employee_id}
* https://employees.corporate.com/${employee_id}
* https://bike-contracts.corporate.com/${contract_id}

When Meshcaline Proxy is configured as reverse proxy for all three services, then the App team would trigger a regular 
HTTP request to the employee search service with 
```
GET https://search.corporate.com/emplyees?manager=${manager_employee_id}
```
along with an extended graphql query in the http header {X-Meshcal}:

```graphql
query {
    items @GET(fragment:"details", type:"employee", href:"https://employees.corporate.com/${employee_id}") : 
        { employee_id }
}

fragment details on employee 
@GET(fragment:"contract_details", type:"bike_contract" href:"https://bike-contracts.corporate.com/${bike_contract_nr}")
{ name, first_name }

fragment contract_details on bike_contract
{ contract_number, start_date, end_date, bike_type, monthly_rate }
```

When Meshcaline Proxy receives this request it does the following:
* It extracts the mescal query from the HTTP header
* It forwards the request to the search service
* When receiving response, it applies the main graphql query on the response body. It detects in the respose the items array 
  references the main query's selection set. For each item it does two things:
  * apply the specified graphQL selection set and with that reduce a returned item object to just an employee_id 
  * detect Meshcaline Proxy's GET directive and construct a new HTTP GET request to the employee details service. For this request
    it constructs the URL by applying the values of the current item to the URL-template of the directives href argument.
* While Meshcaline proxy starts steaming back the transformed result of the proxied search request, it executes the constructed
  HTTP requests to the employee details service.
* As those responses reach the proxy, it does again two things for each response:
  * filter down the response by applying the selection set of the fragment and type that was referenced by the @GET directive (here "details" and "employee") to just name and first_name of the employee
  * construct a new downstream HTTP request to retrieve the details of the bike_contract
* Also the responses of those additional downstream requests get filtered to the requested attribute, but since there is no additional 
  Meshcaline directive, those responses trigger no futher subrequests.

As a result the Meshcaline Proxy will respond the following body:

```json
[
    { 
        "url" : "https://search.corporate.com/emplyees?manager=17",
        "body" : {
            "items": [
                { "employee_id" : 1234, "details" : { "href":"https://employees.corporate.com/1234" } },
                { "employee_id" : 2345, "details" : { "href":"https://employees.corporate.com/2345" } }
            ]
        }
    },
    { 
        "url" : "https://employees.corporate.com/1234",
        "body" : { "name" : "Frank", "last_name": "Jackson", "contract_details" : { "href":"https://bike-contracts.corporate.com/17" } }
    },
    { 
        "url" : "https://employees.corporate.com/2345",
        "body" : { "name" : "Peter", "last_name": "Myers", "contract_details" : { "href":"https://bike-contracts.corporate.com/35" } }
    },
    { 
        "url" : "https://bike-contracts.corporate.com/35",
        "body" : { "contract_number": 35, "start_date": "2024-08-12", "end_date": "2027-08-11", "bike_type": "city" , "monthly_rate": 1800 }
    },
    { 
        "url" : "https://bike-contracts.corporate.com/17",
        "body" : { "contract_number": 35, "start_date": "2024-05-01", "end_date": "2027-04-30", "bike_type": "tracking" , "monthly_rate": 2100 }
    }
]
```

This response is a composition of the filtered responses of each microservice request required to construct the response.
The composition is represented as a list of HTTP responses, each represented by at least the URL of the HTTP resource and
the filtered body of the response.

Each content element in any of the responses that is annotated in the graphQL query with one of Meshcaline Proxy's 
subrequest directives (here @GET) is enriched with a minimal
[Meshcaline hypertext control](https://meshcaline.org/basics/#hypertext-controls) that defined the subrequest and uses the
name of the graphQL's fragment used to process the response. 

Contrary to most other BFF implementations, Meshcaline Proxy doesn't hide the services that contribute to the business process
that a client application wants to implement. The philosophy behind Meshcaline Proxy is to treat microservice as first 
class citizens that applications directly interact with (see more in [Why Meshcaline Proxy](why.md)).

The list of responses does not have a specific order. It is only guaranteed that responses of subrequests arrive later
than the response they originate from.

Since the response is streamed back to the App, applications can leverage streaming parsers. This allows building a responsive UI,
where the App already start displaying individual content elements (e.g displaying the employee names) before the full response
has been arrived.


