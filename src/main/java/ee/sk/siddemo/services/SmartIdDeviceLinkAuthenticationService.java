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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.UserDocumentNumberRequest;
import ee.sk.siddemo.model.UserRequest;
import ee.sk.smartid.RpChallengeGenerator;
import ee.sk.smartid.SignatureAlgorithm;
import ee.sk.smartid.exception.useraction.SessionTimeoutException;
import ee.sk.smartid.exception.useraction.UserRefusedException;
import ee.sk.smartid.rest.dao.DeviceLinkInteraction;
import ee.sk.smartid.rest.dao.DeviceLinkSessionResponse;
import ee.sk.smartid.rest.dao.HashAlgorithm;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.AuthenticationCertificateLevel;
import ee.sk.smartid.AuthenticationResponseMapper;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.dao.SessionStatus;
import ee.sk.smartid.util.DeviceLinkUtil;
import jakarta.servlet.http.HttpSession;

@Service
public class SmartIdDeviceLinkAuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdDeviceLinkAuthenticationService.class);

    private final SmartIdClient smartIdClient;
    private final SmartIdSessionsStatusService smartIdSessionsStatusService;

    @Value("${sid.auth.displayText}")
    private String displayText;

    public SmartIdDeviceLinkAuthenticationService(SmartIdClient smartIdClient,
                                                  SmartIdSessionsStatusService smartIdSessionsStatusService) {
        this.smartIdClient = smartIdClient;
        this.smartIdSessionsStatusService = smartIdSessionsStatusService;
    }

    public void startAuthentication(HttpSession session) {
        String rpChallenge = RpChallengeGenerator.generate();
        var authenticationCertificateLevel = AuthenticationCertificateLevel.QUALIFIED;
        List<DeviceLinkInteraction> interactions = List.of(DeviceLinkInteraction.displayTextAndPIN(displayText));
        DeviceLinkSessionResponse response = smartIdClient.createDeviceLinkAuthentication()
                .withRpChallenge(rpChallenge)
                .withCertificateLevel(authenticationCertificateLevel)
                .withSignatureAlgorithm(SignatureAlgorithm.RSASSA_PSS)
                .withHashAlgorithm(HashAlgorithm.SHA3_512)
                .withInteractions(interactions)
                .withShareMdClientIpAddress(true)
                .initAuthenticationSession();
        Instant responseReceivedTime = Instant.now();

        updateSession(session, rpChallenge, authenticationCertificateLevel, response, responseReceivedTime, interactions);

        smartIdSessionsStatusService.startPolling(session, response.getSessionID());
    }

    public void startAuthentication(HttpSession session, UserRequest userRequest) {
        String rpChallenge = RpChallengeGenerator.generate();
        var semanticsIdentifier = new SemanticsIdentifier(SemanticsIdentifier.IdentityType.PNO, userRequest.getCountry(), userRequest.getNationalIdentityNumber());
        var requestedCertificateLevel = AuthenticationCertificateLevel.QUALIFIED;
        List<DeviceLinkInteraction> interactions = List.of(DeviceLinkInteraction.displayTextAndPIN(displayText));
        DeviceLinkSessionResponse response = smartIdClient.createDeviceLinkAuthentication()
                .withRpChallenge(rpChallenge)
                .withSemanticsIdentifier(semanticsIdentifier)
                .withCertificateLevel(requestedCertificateLevel)
                .withSignatureAlgorithm(SignatureAlgorithm.RSASSA_PSS)
                .withHashAlgorithm(HashAlgorithm.SHA3_512)
                .withShareMdClientIpAddress(true)
                .withInteractions(interactions)
                .initAuthenticationSession();
        Instant responseReceivedTime = Instant.now();

        updateSession(session, rpChallenge, requestedCertificateLevel, response, responseReceivedTime, interactions);

        smartIdSessionsStatusService.startPolling(session, response.getSessionID());
    }

    public void startAuthentication(HttpSession session, UserDocumentNumberRequest userDocumentNumberRequest) {
        String rpChallenge = RpChallengeGenerator.generate();
        var requestedCertificateLevel = AuthenticationCertificateLevel.QUALIFIED;
        List<DeviceLinkInteraction> interactions = List.of(DeviceLinkInteraction.displayTextAndPIN(displayText));
        DeviceLinkSessionResponse response = smartIdClient.createDeviceLinkAuthentication()
                .withRpChallenge(rpChallenge)
                .withDocumentNumber(userDocumentNumberRequest.getDocumentNumber())
                .withCertificateLevel(requestedCertificateLevel)
                .withSignatureAlgorithm(SignatureAlgorithm.RSASSA_PSS)
                .withHashAlgorithm(HashAlgorithm.SHA3_512)
                .withShareMdClientIpAddress(true)
                .withInteractions(interactions)
                .initAuthenticationSession();
        Instant responseReceivedTime = Instant.now();

        updateSession(session, rpChallenge, requestedCertificateLevel, response, responseReceivedTime, interactions);

        smartIdSessionsStatusService.startPolling(session, response.getSessionID());
    }

    public boolean checkAuthenticationStatus(HttpSession session) {
        Optional<SessionStatus> sessionStatus = smartIdSessionsStatusService.getSessionsStatus(session.getId());
        return sessionStatus
                .map(status -> {
                    if (status.getState().equals("COMPLETE")) {
                        saveValidateResponse(session, status);
                        session.setAttribute("session_status", "COMPLETED");
                        logger.debug("Mobile device IP address: {}", status.getDeviceIpAddress());
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    private static void updateSession(HttpSession session,
                                      String rpChallenge,
                                      AuthenticationCertificateLevel certificateLevel,
                                      DeviceLinkSessionResponse response,
                                      Instant responseReceivedTime,
                                      List<DeviceLinkInteraction> interactions) {
        session.setAttribute("rpChallenge", rpChallenge);
        session.setAttribute("requestedCertificateLevel", certificateLevel);
        session.setAttribute("sessionSecret", response.getSessionSecret());
        session.setAttribute("sessionToken", response.getSessionToken());
        session.setAttribute("sessionID", response.getSessionID());
        session.setAttribute("deviceLinkBase", response.getDeviceLinkBase().toString());
        session.setAttribute("responseReceivedTime", responseReceivedTime);
        session.setAttribute("interactions", DeviceLinkUtil.encodeToBase64(interactions));
    }

    private static void saveValidateResponse(HttpSession session, SessionStatus status) {
        try {
            // validate sessions status for device link authentication
            var deviceLinkAuthenticationResponse = AuthenticationResponseMapper.from(status);
            session.setAttribute("authentication_response", deviceLinkAuthenticationResponse);
        } catch (SessionTimeoutException | UserRefusedException ex) {
            throw new SidOperationException(ex.getMessage());
        }
    }
}
