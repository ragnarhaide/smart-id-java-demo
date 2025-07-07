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

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ee.sk.siddemo.model.UserDocumentNumberRequest;
import ee.sk.siddemo.model.UserRequest;
import ee.sk.smartid.AuthenticationCertificateLevel;
import ee.sk.smartid.DeviceLinkAuthenticationSessionRequestBuilder;
import ee.sk.smartid.RpChallengeGenerator;
import ee.sk.smartid.SignatureAlgorithm;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.dao.AuthenticationSessionRequest;
import ee.sk.smartid.rest.dao.DeviceLinkInteraction;
import ee.sk.smartid.rest.dao.DeviceLinkSessionResponse;
import ee.sk.smartid.rest.dao.HashAlgorithm;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.rest.dao.SessionStatus;
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
        DeviceLinkAuthenticationSessionRequestBuilder builder = smartIdClient.createDeviceLinkAuthentication()
                .withRpChallenge(rpChallenge)
                .withCertificateLevel(authenticationCertificateLevel)
                .withSignatureAlgorithm(SignatureAlgorithm.RSASSA_PSS)
                .withHashAlgorithm(HashAlgorithm.SHA3_512)
                .withInteractions(List.of(DeviceLinkInteraction.displayTextAndPIN(displayText)))
                .withShareMdClientIpAddress(true);
        DeviceLinkSessionResponse response = builder.initAuthenticationSession();
        AuthenticationSessionRequest request = builder.getAuthenticationSessionRequest();

        updateSession(session, response, rpChallenge, request);

        smartIdSessionsStatusService.startPolling(session, response.getSessionID());
    }

    public void startAuthentication(HttpSession session, UserRequest userRequest) {
        String rpChallenge = RpChallengeGenerator.generate();
        var semanticsIdentifier = new SemanticsIdentifier(SemanticsIdentifier.IdentityType.PNO, userRequest.getCountry(), userRequest.getNationalIdentityNumber());
        var requestedCertificateLevel = AuthenticationCertificateLevel.QUALIFIED;
        List<DeviceLinkInteraction> interactions = List.of(DeviceLinkInteraction.displayTextAndPIN(displayText));
        DeviceLinkAuthenticationSessionRequestBuilder builder = smartIdClient.createDeviceLinkAuthentication()
                .withRpChallenge(rpChallenge)
                .withSemanticsIdentifier(semanticsIdentifier)
                .withCertificateLevel(requestedCertificateLevel)
                .withSignatureAlgorithm(SignatureAlgorithm.RSASSA_PSS)
                .withHashAlgorithm(HashAlgorithm.SHA3_512)
                .withShareMdClientIpAddress(true)
                .withInteractions(interactions);
        DeviceLinkSessionResponse response = builder.initAuthenticationSession();
        AuthenticationSessionRequest request = builder.getAuthenticationSessionRequest();

        updateSession(session, response, rpChallenge, request);

        smartIdSessionsStatusService.startPolling(session, response.getSessionID());
    }

    public void startAuthentication(HttpSession session, UserDocumentNumberRequest userDocumentNumberRequest) {
        String rpChallenge = RpChallengeGenerator.generate();
        var requestedCertificateLevel = AuthenticationCertificateLevel.QUALIFIED;
        List<DeviceLinkInteraction> interactions = List.of(DeviceLinkInteraction.displayTextAndPIN(displayText));
        DeviceLinkAuthenticationSessionRequestBuilder builder = smartIdClient.createDeviceLinkAuthentication()
                .withRpChallenge(rpChallenge)
                .withDocumentNumber(userDocumentNumberRequest.getDocumentNumber())
                .withCertificateLevel(requestedCertificateLevel)
                .withSignatureAlgorithm(SignatureAlgorithm.RSASSA_PSS)
                .withHashAlgorithm(HashAlgorithm.SHA3_512)
                .withShareMdClientIpAddress(true)
                .withInteractions(interactions);
        DeviceLinkSessionResponse response = builder.initAuthenticationSession();
        AuthenticationSessionRequest request = builder.getAuthenticationSessionRequest();

        updateSession(session, response, rpChallenge, request);

        smartIdSessionsStatusService.startPolling(session, response.getSessionID());
    }

    private static void updateSession(HttpSession session, DeviceLinkSessionResponse response, String rpChallenge, AuthenticationSessionRequest request) {
        session.setAttribute("sessionID", response.getSessionID());
        session.setAttribute("rpChallenge", rpChallenge);
        session.setAttribute("sessionInitResponse", response);
        session.setAttribute("authenticationSessionRequest", request);
        session.setAttribute("interactions", request.interactions());
    }

    public boolean checkAuthenticationStatus(HttpSession session) {
        Optional<SessionStatus> sessionStatus = smartIdSessionsStatusService.getSessionsStatus(session.getId());
        return sessionStatus
                .map(status -> {
                    if (status.getState().equals("COMPLETE")) {
                        session.setAttribute("authenticationSessionStatus", status);
                        session.setAttribute("session_status", "COMPLETED");
                        logger.debug("Mobile device IP address: {}", status.getDeviceIpAddress());
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }
}
