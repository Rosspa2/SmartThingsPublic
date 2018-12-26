/**
 *  ISpy Motion Detection
 *
 *  Copyright 2018 Ross Adams
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "ISpy Motion Detection", namespace: "rossaIspy", author: "Ross Adams") {
		capability "Refresh"
		capability "Switch"
		capability "Health Check"

		capability "Actuator"

		command "clearDigestAuthData"
	}

	tiles(scale: 2) {
		standardTile("switchDisplayAction", "device.switch", inactiveLabel: true, width:6, height:1, decoration: "flat") {
			state "unknown", label:'check configuration', icon:"st.Home.home9", backgroundColor:"#e50000"
			state "turningon", label:'turning on', icon:"st.Home.home9", backgroundColor:"#00a0dc", nextState:"on"
			state "on", label:'${name}', action:"off", icon:"st.Home.home9", backgroundColor:"#00a0dc", nextState:"turningoff"
			state "turningoff", label:'turning off', icon:"st.Home.home9", backgroundColor:"#ffffff", nextState:"off"
			state "off", label:'${name}', action:"on", icon:"st.Home.home9", backgroundColor:"#ffffff", nextState:"turningon"
		}
		standardTile("refresh", "device.switch", width: 2, height: 2, decoration: "flat") {
			state "icon", action:"refresh", icon:"st.secondary.refresh", defaultState: true
		}
		main "switchDisplayAction"
		details(["switchDisplayAction", "refresh"])
	}

	preferences {
		input name: "DeviceId", type: "text", title: "Camera ID", description: "Enter the Camera's Device ID, not the name", displayDuringSetup: true, required: true
		input name: "ISpyIP", type: "text", title: "ISPY IP Address or name of machine", description: "Enter the ip address or machine name of the IP ISpy Server - make the hub has access", displayDuringSetup: true, required: true
        input name: "ISpyPort", type: "text", title: "ISPY Port", description: "Enter the web port defined for the ISpy Server (8080 for example)", displayDuringSetup: true, required: true
	}
}

def installed() {
	log.debug("installed()")
	sendEvent(name:"switch", value:"unknown")
}

def updated() {
	log.debug("updated()")

	if (state.DeviceId != DeviceId) {
		state.DeviceId = DeviceId
		log.debug("New Camera ID: ${state.DeviceId}")
	}

	if (state.ISpyIP != ISpyIP) {
		state.ISpyIP = ISpyIP
		log.debug("ISPY IP: ${state.ISpyIP}")
	}

	if (state.ISpyPort != ISpyPort) {
		state.ISpyPort = ISpyPort
		log.debug("New ISpyPort: ${state.ISpyPort}")
	}

	// Ping the camera every 5 minutes for health-check purposes
	unschedule()
	runEvery5Minutes(refresh)
	// After checkInterval seconds have gone by, ST sends one last ping() before marking as offline
	// set checkInterval to the length of 2 failed refresh()es (plus an extra minute)
	sendEvent(name: "checkInterval", value: 2 * 5 * 60 + 60, displayed: false, data: [protocol : "LAN"])

	refresh()
}


def setCameraState(on) {
	def cameraState = on ? "/bringonline?ot=2&oid=" : "/takeoffline?ot=2&oid="
	def action = createCameraRequest("GET", cameraState)
	// log.debug("Setting motion detection setting ${on} with request: ${action}")
	sendHubCommand(action)
}

def on() {
	log.debug("on()")
    state.TurningOn = true
	setCameraState(true)
}

def off() {
	log.debug("off()")
    state.TurningOn = false
	setCameraState(false)
}


def ping() {
	log.debug("ping()")
	healthCheck()
}

def refresh() {
	log.debug("refresh()")
	checkCameraState()
}

def healthCheck() {
	log.debug("healthCheck()")
	def action = createCameraRequest("GET", "/getobjectlist?ot=2&oid=")
	sendHubCommand(action)
}

def parseResponse(physicalgraph.device.HubResponse response) {
	log.debug("parseResponse()")
	return parse(response.description)
}

def parse(String description) {
	log.debug("parse()")
	def msg = parseLanMessage(description)
	log.debug("Body: ${msg.body}")

	// Handle unknown responses
	if (!state.lastRequest || state.lastRequest.requestId != msg.requestId) {
		log.debug("parse() received message likely meant for other device handler (requestIds don't match): ${msg}")
        log.debug("msg requestid: ${msg.requestId}, last request id: ${state.lastRequest.requestId}")
		return
	}

	if (msg.status == 200) {
		// Delete last request info since it succeeded
		def lastRequest = state.lastRequest
		state.remove("lastRequest")

		// use lastRequest uri to decide how to handle response
		if (lastRequest.uri.contains("/getobjectlist?")) {
			handleInformationResponse(msg, lastRequest)
			return
		} // camera coming online or offline.
		else if (lastRequest.uri.contains("line?")) {
			handleOnlineEvent(msg, lastRequest)
			return
		}
		else {
			log.debug("Not sure how to handle response from ${lastRequest.uri}")
		}
	}
	else {
		log.debug("parse() received failure message: ${msg}")
	}
}


// Response handlers
def handleInformationResponse(response, lastRequest) {
	log.debug("handleInformationResponse(): ${response.body}")
    if (response.body.contains("Online"))
    {	
    	sendEvent(name:"switch", value:"on")
        log.debug("setting state of switch to on")
	} else {
    	log.debug("setting state of switch to off")
 		sendEvent(name:"switch", value:"off")
    }
     
	
}

def handleOnlineEvent(response, lastRequest) {
	log.debug("handleOnlineEvent(): ${response.body}")
    if (response.body.contains("OK")) {
		if(state.TurningOn)
        { 
        	log.debug("setting state of switch to on")
        	sendEvent(name:"switch", value:"on")
        }
    	else {
    		log.debug("setting state of switch to off")
        sendEvent(name:"switch", value:"off")
    	}
        state.remove("TurningOn")
    } 
}

def checkCameraState() {
	log.debug("checkCameraState()")
	def action = createCameraRequest("GET", "/getobjectlist?ot=2&oid=")
	sendHubCommand(action)
}

private physicalgraph.device.HubAction createCameraRequest(method, uri, isRetry = false)
{
	
    // add the device in
    uri = "${uri}${state.DeviceId}"
    
    log.debug("Creating camera request with method: ${method}, uri: ${uri}, isRetry: ${isRetry}")

	if (state.ISpyPort == null || state.ISpyIP == null || state.DeviceId == null) {
		log.debug("Cannot check camera status, ISPY IP address, ISPY Port or DeviceID is not set.")
		return null
	}
    
    log.debug("getting ready to make request")

	try {
        def headers = [
			HOST: "${state.ISpyIP}:${state.ISpyPort}"
		]
		def data = [
			method: method,
			path: uri,
			headers: headers
		]
		// Use a custom callback because this seems to bypass the need for DNI to be hex IP:port or MAC address
		def action = new physicalgraph.device.HubAction(data, null, [callback: parseResponse])
	    log.debug("Created new HubAction, requestId: ${action.requestId}")

		// Persist request info in case we need to repeat it
		state.lastRequest = [:]
		state.lastRequest.method = method
		state.lastRequest.uri = uri
		state.lastRequest.isRetry = isRetry
		state.lastRequest.requestId = action.requestId

		return action
	}
	catch (Exception e) {
		log.debug("Exception creating HubAction for method: ${method} and URI: ${uri}")
	}
}


