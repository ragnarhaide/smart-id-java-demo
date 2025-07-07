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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ee.sk.siddemo.model.DynamicContent;
import ee.sk.smartid.DeviceLinkBuilder;
import ee.sk.smartid.DeviceLinkType;
import ee.sk.smartid.QrCodeGenerator;
import ee.sk.smartid.SessionType;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.dao.DeviceLinkSessionResponse;
import jakarta.servlet.http.HttpSession;

@Service
public class DynamicContentService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicContentService.class);

    public final SmartIdClient smartIdClient;

    public DynamicContentService(SmartIdClient smartIdClient) {
        this.smartIdClient = smartIdClient;
    }

    public DynamicContent getDynamicContent(HttpSession session, SessionType sessionType) {
        String digest = (String) session.getAttribute("rpChallenge");
        String interactions = (String) session.getAttribute("interactions");
        DeviceLinkSessionResponse sessionInitResponse = (DeviceLinkSessionResponse) session.getAttribute("sessionInitResponse");
        return getDynamicContent(sessionType, digest, interactions, sessionInitResponse);
    }

    public DynamicContent getDynamicContent(SessionType sessionType,
                                            String digest,
                                            String interactions,
                                            DeviceLinkSessionResponse deviceLinkSessionResponse) {
        long elapsedSeconds = Duration.between(deviceLinkSessionResponse.getReceivedAt(), Instant.now()).getSeconds();
        logger.info("Dynamic content elapsed seconds: {}", elapsedSeconds);

        String relyingPartyName = smartIdClient.getRelyingPartyName();

        URI dynamicLink = new DeviceLinkBuilder()
                .withSchemeName("smart-id-demo")
                .withDeviceLinkBase(deviceLinkSessionResponse.getDeviceLinkBase().toString())
                .withDeviceLinkType(DeviceLinkType.WEB_2_APP)
                .withSessionType(sessionType)
                .withSessionToken(deviceLinkSessionResponse.getSessionToken())
                .withLang("eng")
                .withInitialCallbackUrl("https://localhost:8080/callback")
                .withRelyingPartyName(relyingPartyName)
                .withInteractions(interactions)
                .withDigest(digest)
                .buildDeviceLink(deviceLinkSessionResponse.getSessionSecret());

        URI qrLink = new DeviceLinkBuilder()
                .withSchemeName("smart-id-demo")
                .withDeviceLinkBase(deviceLinkSessionResponse.getDeviceLinkBase().toString())
                .withDeviceLinkType(DeviceLinkType.QR_CODE)
                .withSessionType(sessionType)
                .withSessionToken(deviceLinkSessionResponse.getSessionToken())
                .withLang("eng")
                .withElapsedSeconds(elapsedSeconds)
                .withRelyingPartyName(relyingPartyName)
                .withInteractions(interactions)
                .withDigest(digest)
                .buildDeviceLink(deviceLinkSessionResponse.getSessionSecret());

        String qrDataUri = QrCodeGenerator.generateDataUri(qrLink.toString());

        return new DynamicContent(dynamicLink, qrDataUri);
    }
}
