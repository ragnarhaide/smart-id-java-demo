package ee.sk.siddemo.services;

/*-
 * #%L
 * Smart-ID sample Java client
 * %%
 * Copyright (C) 2018 - 2025 SK ID Solutions AS
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.apache.hc.client5.http.utils.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.smartid.exception.useraction.SessionTimeoutException;
import ee.sk.smartid.exception.useraction.UserRefusedException;
import ee.sk.smartid.ErrorResultHandler;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.dao.DeviceLinkSessionResponse;
import ee.sk.smartid.rest.dao.SessionResult;
import ee.sk.smartid.rest.dao.SessionStatus;
import jakarta.servlet.http.HttpSession;

@Service
public class SmartIdDynamicLinkCertificateChoiceService {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdDynamicLinkCertificateChoiceService.class);

    private final SmartIdClient smartIdClient;
    private final SmartIdSessionsStatusService smartIdSessionsStatusService;

    public SmartIdDynamicLinkCertificateChoiceService(SmartIdClient smartIdClient,
                                                      SmartIdSessionsStatusService smartIdSessionsStatusService) {
        this.smartIdClient = smartIdClient;
        this.smartIdSessionsStatusService = smartIdSessionsStatusService;
    }

    public void startCertificateChoice(HttpSession session) {
        // TODO - 16.12.24: uncomment when anonymous certificate choice is supported by RP API v3
//        DynamicLinkSessionResponse response = this.smartIdClient.createDynamicLinkCertificateRequest()
//                .withCertificateLevel(CertificateLevel.QUALIFIED)
//                .withShareMdClientIpAddress(true)
//                .initCertificateChoice();
        DeviceLinkSessionResponse response = new DeviceLinkSessionResponse();
        response.setSessionID("fake-session-id");
        response.setSessionToken("fake-token");
        response.setSessionSecret(Base64.encodeBase64String("fake-secret".getBytes(StandardCharsets.UTF_8)));

        Instant responseReceivedTime = Instant.now();

        session.setAttribute("sessionID", response.getSessionID());
        session.setAttribute("sessionToken", response.getSessionToken());
        session.setAttribute("sessionSecret", response.getSessionSecret());
        session.setAttribute("responseReceivedTime", responseReceivedTime);

        // TODO - 16.12.24: uncomment when anonymous certificate choice session can be created
//        smartIdSessionsStatusService.startPolling(session, response);
    }

    public boolean checkCertificateChoiceStatus(HttpSession session) {
        // TODO - 16.12.24: uncomment when anonymous certificate choice is supported by RP API v3
        // Optional<SessionStatus> sessionStatus = smartIdSessionsStatusService.getSessionsStatus(session.getId());

        // Dummy session status to simulate querying the session status, displaying dynamic sessions for 10seconds
        // Remove after RP API v3 supports dynamic-link certificate choice
        Instant responseReceivedTime = (Instant) session.getAttribute("responseReceivedTime");
        long elapsedSeconds = Duration.between(responseReceivedTime, Instant.now()).getSeconds();
        SessionStatus status = dummySessionsStatus(elapsedSeconds);
        Optional<SessionStatus> sessionStatus = Optional.of(status);

        return sessionStatus
                .map(ss -> {
                    if (ss.getState().equals("COMPLETE")) {
                        saveValidateResponse(session, ss);
                        session.setAttribute("session_status", "COMPLETED");
                        logger.debug("Mobile device IP address: {}", ss.getDeviceIpAddress());
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    // TODO - 17.12.24: only used for simulating querying the session status, remove after actuall functionality can be used
    private static SessionStatus dummySessionsStatus(long elapsedSeconds) {
        SessionStatus status;
        if (elapsedSeconds > 10) {
            SessionResult result = new SessionResult();
            result.setEndResult("OK");
            result.setDocumentNumber("PNOEE-1234567890-MOCK-Q");

            status = new SessionStatus();
            status.setState("COMPLETE");
            status.setResult(result);
        } else {
            status = new SessionStatus();
            status.setState("RUNNING");
        }
        return status;
    }

    private static void saveValidateResponse(HttpSession session, SessionStatus status) {
        try {
            // validate sessions status does not contain errors
            if (!"OK".equals(status.getResult().getEndResult())) {
                ErrorResultHandler.handle(status.getResult());
            }
            // TODO - 17.12.24: uncomment when anonymous certificate choice is supported by RP API v3
//            X509Certificate certificate = CertificateParser.parseX509Certificate(status.getCert().getValue());
//            String distinguishedName = certificate.getSubjectX500Principal().getName();
            String distinguishedName = "CN=John Doe, OU=IT, O=Company, C=EE";
            session.setAttribute("distinguishedName", distinguishedName);
            session.setAttribute("documentNumber", status.getResult().getDocumentNumber());
        } catch (SessionTimeoutException | UserRefusedException ex) {
            throw new SidOperationException(ex.getMessage());
        }
    }
}
