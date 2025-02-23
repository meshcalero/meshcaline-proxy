---
layout: default
title: Why Meshcaline Proxy?
---

# Why Meshcaline Proxy?

The Meshcaline Proxy addresses problems that materialized with the introduction of distributed microservice 
architectures and provides an alternative to backend-for-frontend architectures that come with their own
challenges.

## The Early Days of Webservice API
The early webservice API were implemented as monoliths that provided individual endpoints for the specific 
business processes that were requested by a client. 

The monolith had implemented all capabilities required to support the business process.
As business processes became more complex, those monolith implementations became more complex too and over time
no longer maintainable.

## The Rise of Microservices 
The microservice architecture did address this problem. In this architecture complex business processes implementaions 
are split into distributed and self-contained microservices. Each service is specialized in one specific aspect required for 
a business process. With that the complexity of each service became significantly smaller and the maintenance of their code
base became simpler. Thanks to being optimized for a specific task, their performance increased too. And when best practices
and pattern for microservice design emerged, the reusability of a given microservice in the context of multiple
business processes became better too.

In a microservice architecture the client applications have to take over the composition of those individual services to 
model the business process. While the software engineers building the client applications had no conceptual problem
taking over this task, it turned out that the responsiveness of the client applications became worse when switching
from a monolith to a webservice architecture. 

Even when the sum of the processing times of the individual microservices
involved in a given business process was often smaller than with the old monolith, often the total processing time was 
longer, esp. on mobile devices. The primary reason for this was network latency. Whereas in the monolith setup 
the client usually did one (or maybe a handful) of client/server requests, in a microservice architecture the clients
had to do compose the business process by doing dozens of requests to the various microservice. And since those
requests went over public, often mobile internet, the network overhead of sending a request and receiving the response,
before the next request could be executed (as those requests were usually dependent on the response of the 
previous request) became the deciding factor for the overall performance of the business process. And to make it worse:
No technological improvement could have fundamentally solved this problem, because it was defined by the law of physics: 
Information can't travel faster than light, hence there is nothing we can ever do to get rid of the fact that it 
takes ~100ms to transfer one bit of information between Frankfurt and New York. Regardless how fast servers became,
the network latency stayed the limiting factor for end-to-end speed.

Another problem was the size of information transferred: Given that the involved microservices were usually not
dedicated to a single business, but instead provided a specific functionality for a given sub-domain involved in a 
business process, those services often responded information not required for the specific business process a client 
wanted to implement. This unnecessary information was consuming additional bandwidth and increased the latency further.

## Backend-For-Frontend Services: One service to rule them all
To address both problem the pattern of Backend-For-Frontend (BFF) service emerged. The BFF service
played a man in the middle between the client application on the end users device and the various microservice involved
in a business process. It took over the orchestration of the microservices from the client application and returned to
the client application only the subset of information required by the client for the implementation of the 
specific business process. Since those BFF services were usually co-hosted in the same datacenter as the microservice, 
network latency caused by physical distance was eliminated.

While BFF services often were initially introduced by front-end developers to overcome their application's 
latency issues, it became quickly apparent that backend software development (esp. when operated at scale) 
required a different set of capabilities than front end development. Latest when dedicated frameworks for such 
BFF implementations became popular (esp. graphQL), usually BFF services were maintained by dedicated teams.

Unfortunately those BFF teams quickly became the bottleneck for introducing new features. When the application
team needed new functionality, it now had to get the corresponding feature on the roadmap of at least two teams: 
First on the team(s) owning the microservice(s) responsible for providing the new capability, and then on the BFF team
to adjust an existing business process or adding a new one for the new capability. To make it worse, the BFF team
often had a different understanding of appropriate modelling of a business process than the microservice team that 
enabled the underlying functionality. As a consequence the design paradigm of the BFF API was often fundamental
different than the one of the microservice. And as the microservices evolved over time leveraging their design paradigm
it often turned out that adjusting the BFF's API to use the new capability required significant adjustments.

## Introducing Meshcaline Proxy
The Meshcaline Proxy provides an alternative to BFFs. Rather than establishing a dedicated service with its
own API, that is build and maintained by a separate team, it is just a generic piece of infrastructure that
sits between the application and the microservices. Its primary task is to orchestrate multiple webservices calls , 
based on orchestration instructions it receives with an initial request from the client application. Further it
filters out all the information returned by a microservice that is not explicitly requested by the client.

With Meshcaline Proxy, the client application is again in full control of the microservices that it wants
to compose to implement a specific business process. 

### Orchestration beyond proxied microservices
Meshcaline proxy's orchestration capabilities don't stop with the orchestration of the directly proxied services. 
The orchestration instructions can also describe requests to services from completely different contexts:

Imagine a microservice service that returns some address and the client application's business process needs also 
the geo-coordinate of that address.
The orchestration instructions allow the client to ask the proxy to take the address from the microservice's response 
and to trigger another request to Google's geocoder. Both responses will be streamed in one response to the client. 
In that case the proxy will not be able to benefit of the advantages of being co-hosted with the orchestrated 
geocoding service, but given that the proxy can trigger this request already much earlier than the client (who 
first would have to wait for the arrival of the address over public internet) and (b) being hosted on a data-center 
with significantly better internet connectivity than client devices, it will still have the response much faster 
and can also strip off irrelevant aspects from the response before returning it to the client.



