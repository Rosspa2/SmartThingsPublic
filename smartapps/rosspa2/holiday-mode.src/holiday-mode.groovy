/**
 *  Home and Away
 *
 *  Copyright 2017 Ross Adams
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
definition(
    name: "Holiday Mode",
    namespace: "rosspa2",
    author: "Ross Adams",
    description: "Put your home into holiday mode",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Devices") {
		input "switchs", "capability.switch", title:"Select the switches that you want to work with.", multiple: true, required: true
        }
    section("Times") {
		input "startTime", "time", title: "When do you wnat to turn the devices on", required: true
        input "endTime", "time", title: "When do you want to turn the devices off", required: true
        input "randomTime", "number", required: true
        input "days", "enum", title: "Which days of the week?", required: true, multiple: true, options: ["Monday": "Monday", "Tuesday": "Tuesday", "Wednesday": "Wednesday", "Thursday": "Thursday", "Friday": "Friday"]
        }
	}

def installed() {
	log.debug "Installed with settings: ${settings}"
    log.debug "Calling Install"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}


def initialize() {
	// Subscribe to timmer events
    log.debug "Setting state to false initially"
    state.IsRunning = false
    log.debug "State ${state.IsRunning}"
    ScheduledTask()
}

// Event handlers

def private ScheduledTask() {
 	// makes sure there is a task to run at the right time

    log.debug "Current state ${state.IsRunning}"

    if (state.IsRunning == false) {
    	runOnce(startTime, RunTask) 
    	log.debug "Scheduling task to turn on at ${startTime}"
    } else {
        log.debug "Task running so don't need to reschedule"
    }
}

def private RunTask() {

	log.debug "Running task - current state ${state.IsRunning}"
 
	if (state.IsRunning == true) {
		state.IsRunning = false
        
        for (Switch in switchs) {
        	log.debug "Turning lights off ${Switch}"
        	Switch.off()
        }
    } else {
		state.IsRunning = true
		for (Switch in switchs) {
        	log.debug "Turning lights on ${Switch}"
        	Switch.on()
        }
        log.debug "Scheduling end time ${endTime}"
        runOnce(endTime, RunTask)
        
    }

}

def private CalculateSchedule() {
	// calculate the next run time based on scheduled data
    
}
