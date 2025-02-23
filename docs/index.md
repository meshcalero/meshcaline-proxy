---
layout: default
title: Overview
---

# Welcome to Meshcaline Proxy!

Meshcaline Proxy's mission is to enable the transformation from the **programmable web** to a **composable web**.

It serves two primary audiences:
- **API Providers**
- **Application Developers**

## Why Meshcaline Proxy?

Meshcaline Proxy provides the benefits of **backend-for-frontend (BFF) services** without inheriting their downsides.  
For details, see [Why Meshcaline Proxy](why.md).

### How It Works

Meshcaline Proxy is a **generic HTTP proxy** that enables a **universal BFF implementation** as an **infrastructure component**,  
agnostic to the web service APIs it serves.

## Key Characteristics

- Operates on **plain HTTP semantics**.
- Works with **any existing HTTP WebService API**—no adjustments needed.
- Clients can use Meshcaline Proxy **optionally**; if not used, requests are transparently proxied.
- Uses **GraphQL query language** to let clients filter response sizes based on their needs.
- Extends **GraphQL** to dynamically compose multiple API calls into a **single** response.
- **Optimized for minimal latency** with a streaming architecture, supporting **reactive client applications**.
- Special **Meshcaline design principles** simplify composition for client applications.  
  (See [Meshcaline Design Principles](https://meshcaline.org))

🔗 **Find Meshcaline Proxy on [GitHub](https://github.com/meshcalero/meshcaline-proxy)**

---

## Example Scenario: Corporate App

Let’s explore a real-world scenario where Meshcaline Proxy provides a powerful alternative to traditional BFFs.

### The Problem

A **large corporation** has built an app for employees to manage administrative tasks.  
The app uses **multiple microservices**, including:
- **Employee Search Service**
- **Employee Details Service**
- **Bike Contract Service** (for a new company bike leasing program)

The **bike team** wants to display employees' **bike contracts** in the app.  
However, all requests go through a **BFF service**, managed by a separate team.

### The Bottleneck

The App team requests the **BFF team** to create a new endpoint to:
1. Search employees of a given manager.
2. Retrieve employee details.
3. Fetch bike contract details for each employee.
4. Return **only** the required fields for the UI.

🚨 **Problem:**  
The BFF team is overwhelmed with feature requests, and this one gets **backlogged for months**.

Bypassing the BFF service would introduce:
- **High latency** (due to multiple client-server round trips).
- **Design violations** of the existing architecture.

### The Meshcaline Proxy Solution

With **Meshcaline Proxy**, the app team can define their data needs dynamically—without waiting on the BFF team.

#### Microservice Endpoints

Assume the following microservice endpoints:
- **Employee Search:**  
  `GET https://search.corporate.com/employees?manager=${manager_employee_id}`
- **Employee Details:**  
  `GET https://employees.corporate.com/${employee_id}`
- **Bike Contract Details:**  
  `GET https://bike-contracts.corporate.com/${contract_id}`

#### The Request Using Meshcaline Proxy

```
GET https://search.corporate.com/emplyees?manager=${manager_employee_id}
```
along with an extended graphql query in the HTTP header `X-MESHCALINE-QUERY`:

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
This query specifies that:
- The **employee search service** response should be reduced to `employee_id`.
- Additional requests should be made for **employee details** and **bike contract details** dynamically.

### How Meshcaline Proxy Processes This Request

1. Extracts the **GraphQL query** from the `X-MESHCALINE-QUERY` HTTP header.
2. Forwards the request to the **Employee Search Service**.
3. Parses the **response** and:
  - Filters it to return only `employee_id`.
  - Detects the `@GET` directive and **requests employee details** for each employee.
4. Upon receiving **employee details**, it:
  - Filters for `name` and `first_name`.
  - Detects the **bike contract reference** and fetches contract details.
5. Finally, **bike contract details** are retrieved and filtered as requested.

### The Response

The response is a **structured list** of microservice responses, including employee details and contract information.

Each response element contains:
- The **URL** of the microservice request.
- The **filtered** response body.

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
The list of responses does not have a specific order. It is only guaranteed that responses of subrequests arrive later
than the response they originate from.

### Why This Matters

🚀 **Streaming Response**
- Responses are **streamed incrementally**.
- The UI can start rendering **before all data arrives**.

📦 **Efficient Composition**
- The app **avoids direct calls to each microservice**.
- No need to modify existing APIs.

🌐 **Preserves API Transparency**
- Unlike traditional BFFs, Meshcaline Proxy **does not hide microservices**.
- Applications **interact directly** with microservices.

For more details, see [Why Meshcaline Proxy](why.md).  




