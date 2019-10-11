package com.springcloudstreamkafka.publisherooc.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.kafkastream.demo.lib.model.ProcessEvent;
import com.kafkastream.demo.lib.model.ProcessResult;
import com.kafkastream.demo.lib.model.ProcessStatus;
import com.kafkastream.demo.lib.model.ServiceName;
import com.springcloudstreamkafka.publisherooc.dto.ApplicationStatusDetails;
import com.springcloudstreamkafka.publisherooc.model.ApplicationLog;
import com.springcloudstreamkafka.publisherooc.model.ApplicationProcess;
import com.springcloudstreamkafka.publisherooc.model.ApplicationStatus;
import com.springcloudstreamkafka.publisherooc.repository.ApplicationLogRepository;
import com.springcloudstreamkafka.publisherooc.repository.ApplicationProcessRepository;
import com.springcloudstreamkafka.publisherooc.repository.ApplicationStatusRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ApplicationService {

	@NotNull
	private final ApplicationProcessRepository applicationProcessRepository;

	@NotNull
	private final ApplicationStatusRepository applicationStatusRepository;

	@NotNull
	private final ApplicationLogRepository applicationLogRepository;

	public ApplicationStatus initProcess(String applicationNumber) {
		ApplicationStatus appStatus = ApplicationStatus.builder()
				.applicationNumber(applicationNumber)
				.ajdcStatus(ProcessStatus.INPROGRESS)
				.bureauStatus(ProcessStatus.INPROGRESS)
				.createdDate(LocalDateTime.now()).build();
		return applicationStatusRepository.save(appStatus);
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public ApplicationStatus getAppStatus(String applicationNumber) {
		return applicationStatusRepository.findById(applicationNumber).orElse(null);
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public ApplicationStatus updateAppStatus(ProcessResult result) {
		if (result.getServiceName().equals(ServiceName.BUREAU)) {
			return updateAppStatus(result.getApplicationNumber(), "update-burea-status", result.getStatus(), null);
		} else {
			return updateAppStatus(result.getApplicationNumber(), "update-ajdudication-status", null,
					result.getStatus());
		}
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public ApplicationStatus updateAppStatus(String applicationNumber, String action, ProcessStatus bureaStatus,
			ProcessStatus ajdcStatus) {
		ApplicationStatus appStatus = getAppStatus(applicationNumber);
		if (appStatus == null) {
			appStatus = initProcess(applicationNumber);
		} else {
			if (bureaStatus != null) {
				appStatus.setBureauStatus(bureaStatus);
			} else if (ajdcStatus != null) {
				appStatus.setAjdcStatus(ajdcStatus);
			}

			if (bureaStatus != null && ajdcStatus != null) {
				appStatus = applicationStatusRepository.saveAndFlush(appStatus);
			}
		}

		logAppProcess(applicationNumber, action, "Updating Service static in Database", appStatus.getBureauStatus(),
				appStatus.getAjdcStatus());

		return appStatus;
	}

	public void addTolog(String applicationNumber, String logString) {
		log.info(logString);
		ApplicationLog logEvent = ApplicationLog.builder().applicationNumber(applicationNumber).log(logString).build();
		applicationLogRepository.save(logEvent);
	}

	// Log event such as trigger start of burea
	public void logProcessEvent(ProcessEvent event) {
		ApplicationStatus appStatus = getAppStatus(event.getApplicationNumber());
		if (appStatus != null) {
			logAppProcess(event.getApplicationNumber(), event.getAction(), event.getActionDesc(),
					appStatus.getBureauStatus(), appStatus.getAjdcStatus());
		}
	}

	// Log event result such as done burea check sucess/failed
	public void logProcessResult(ProcessResult result) {
		log.debug("(" + result.getApplicationNumber() + ") " + result.getServiceName() + ", " + result.getAction()
				+ ", " + result.getStatus() + ", " + getCurrentTimeString());
		ApplicationStatus appStatus = getAppStatus(result.getApplicationNumber());
		if (appStatus != null) {
			logAppProcess(result.getApplicationNumber(), result.getAction(), result.getActionDesc(),
					appStatus.getBureauStatus(), appStatus.getAjdcStatus());
		}
	}

	public void logAppProcess(String applicationNumber, String applicationAction, String applicationDesc,
			ProcessStatus bureaStatus, ProcessStatus ajdcStatus) {
		ApplicationProcess process = ApplicationProcess.builder().applicationNumber(applicationNumber)
				.applicationAction(applicationAction).applicationDesc(applicationDesc).bureauStatus(bureaStatus)
				.ajdcStatus(ajdcStatus).build();
		applicationProcessRepository.save(process);
	}

	public static String getCurrentTimeString() {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:ms");
		return LocalDateTime.now().format(formatter);
	}
	
	// Below methods are for UI use
	public ApplicationStatusDetails getAllAppStatus() {
		List<ApplicationStatus> allAppStatus = applicationStatusRepository.findAll();
		
		if (allAppStatus != null && allAppStatus.size() > 0) {
			// Look for the last record updated time
			LocalDateTime startedTime = allAppStatus.get(0).getCreatedDate();
			LocalDateTime finishedTime = allAppStatus.get(0).getLastUpdatedDate();
			boolean isProcessFinished = true;
			
			for (ApplicationStatus application : allAppStatus) {
				// Check if process finished 
				if (application.getAjdcStatus().equals(ProcessStatus.INPROGRESS)
						|| application.getBureauStatus().equals(ProcessStatus.INPROGRESS)) {
					isProcessFinished = false;
					break;
				}
				
				if (application.getCreatedDate().isBefore(startedTime)) {
					startedTime = application.getLastUpdatedDate();
				}
				
				if (application.getLastUpdatedDate().isAfter(finishedTime)) {
					finishedTime = application.getLastUpdatedDate();
				}
			}
			
			Duration processDuration = isProcessFinished ? Duration.between(startedTime, finishedTime) : null;
			Long durationInSeconds = isProcessFinished ? processDuration.getSeconds() : null;
			
			return ApplicationStatusDetails.builder()
					.applicationStatus(allAppStatus)
					.processDuration(processDuration)
					.processDurationInSeconds(durationInSeconds)
					.isProcessFinished(isProcessFinished).build();
		} else {
			return ApplicationStatusDetails.builder()
					.applicationStatus(allAppStatus).build();
		}
	}
}
