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

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.SignatureBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ee.sk.siddemo.exception.FileUploadException;
import ee.sk.siddemo.exception.SidOperationException;
import ee.sk.siddemo.model.UserDocumentNumberRequest;
import ee.sk.siddemo.model.UserRequest;
import ee.sk.smartid.HashType;
import ee.sk.smartid.exception.useraccount.CertificateLevelMismatchException;
import ee.sk.smartid.exception.useraction.SessionTimeoutException;
import ee.sk.smartid.exception.useraction.UserRefusedException;
import ee.sk.smartid.exception.useraction.UserSelectedWrongVerificationCodeException;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.CertificateChoiceResponse;
import ee.sk.smartid.CertificateLevel;
import ee.sk.smartid.SignableData;
import ee.sk.smartid.SignatureResponseMapper;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.dao.NotificationInteraction;
import ee.sk.smartid.rest.dao.NotificationSignatureSessionResponse;
import ee.sk.smartid.rest.dao.SessionStatus;
import jakarta.servlet.http.HttpSession;

@Service
public class SmartIdNotificationBasedSigningService {

    private final SmartIdClient smartIdClient;
    private final SmartIdSessionsStatusService sessionStatusService;
    private final SmartIdNotificationBasedCertificateChoiceService notificationCertificateChoiceService;


    public SmartIdNotificationBasedSigningService(SmartIdClient smartIdClient,
                                                  SmartIdSessionsStatusService sessionStatusService,
                                                  SmartIdNotificationBasedCertificateChoiceService notificationCertificateChoiceService) {
        this.smartIdClient = smartIdClient;
        this.sessionStatusService = sessionStatusService;
        this.notificationCertificateChoiceService = notificationCertificateChoiceService;
    }

    public String startSigningWithDocumentNumber(HttpSession session, UserDocumentNumberRequest userDocumentNumberRequest) {
        var signatureCertificateLevel = CertificateLevel.QUALIFIED;

        X509Certificate certificate = smartIdClient
                .createCertificateByDocumentNumber()
                .withDocumentNumber(userDocumentNumberRequest.getDocumentNumber())
                .withCertificateLevel(signatureCertificateLevel)
                .getCertificateByDocumentNumber()
                .certificate();


        SignableData signableData = toSignableData(userDocumentNumberRequest.getFile(), certificate, session);
        NotificationSignatureSessionResponse sessionResponse = smartIdClient.createNotificationSignature()
                .withCertificateLevel(signatureCertificateLevel)
                .withSignableData(signableData)
                .withDocumentNumber(userDocumentNumberRequest.getDocumentNumber())
                .withAllowedInteractionsOrder(List.of(NotificationInteraction.verificationCodeChoice("Sign the document!")))
                .initSignatureSession();

        session.setAttribute("sessionID", sessionResponse.getSessionID());
        session.setAttribute("signatureCertificateLevel", signatureCertificateLevel);
        return sessionResponse.getVc().getValue();
    }

    public String startSigningWithPersonCode(HttpSession session, UserRequest userRequest) {
        var signatureCertificateLevel = CertificateLevel.QUALIFIED;
        notificationCertificateChoiceService.startCertificateChoice(session, userRequest, signatureCertificateLevel);
        var signableData = toSignableData(userRequest.getFile(), session);
        var semanticsIdentifier = new SemanticsIdentifier(SemanticsIdentifier.IdentityType.PNO, userRequest.getCountry(), userRequest.getNationalIdentityNumber());
        NotificationSignatureSessionResponse sessionResponse = smartIdClient.createNotificationSignature()
                .withCertificateLevel(signatureCertificateLevel)
                .withSignableData(signableData)
                .withSemanticsIdentifier(semanticsIdentifier)
                .withAllowedInteractionsOrder(List.of(NotificationInteraction.verificationCodeChoice("Sign the document!")))
                .initSignatureSession();

        session.setAttribute("sessionID", sessionResponse.getSessionID());
        session.setAttribute("signatureCertificateLevel", signatureCertificateLevel);
        return sessionResponse.getVc().getValue();
    }

    public void checkSignatureStatus(HttpSession session) {
        String sessionId = (String) session.getAttribute("sessionID");
        if (sessionId == null) {
            throw new SidOperationException("Session ID is missing");
        }
        SessionStatus sessionStatus = sessionStatusService.poll(sessionId);
        saveValidateResponse(session, sessionStatus);
    }

    private SignableData toSignableData(MultipartFile uploadedFile, HttpSession session) {
        return toSignableData(uploadedFile, getCertificate(session), session);
    }

    private SignableData toSignableData(MultipartFile uploadedFile, X509Certificate certificate, HttpSession session) {
        Container container = toContainer(uploadedFile);
        DataToSign dataToSign = toDataToSign(container, certificate);
        saveSigningAttributes(session, dataToSign, container);

        SignableData signableData = new SignableData(dataToSign.getDataToSign());
        signableData.setHashType(HashType.SHA256);  // has to match SignatureDigestAlgorithm used in dataToSign
        return signableData;
    }

    private Container toContainer(MultipartFile userDocumentNumberRequest) {
        DataFile uploadedFile = getUploadedDataFile(userDocumentNumberRequest);

        var configuration = new Configuration(Configuration.Mode.TEST);
        return ContainerBuilder.aContainer()
                .withConfiguration(configuration)
                .withDataFile(uploadedFile)
                .build();
    }

    private X509Certificate getCertificate(HttpSession session) {
        Optional<SessionStatus> certSessionStatus;
        do {
            certSessionStatus = getCertificateChoiceSessionStatus(session);
        } while (certSessionStatus.isEmpty());

        CertificateChoiceResponse certificateChoiceResponse = notificationCertificateChoiceService.getCertificateChoice(session, certSessionStatus.get());
        return certificateChoiceResponse.getCertificate();
    }

    private Optional<SessionStatus> getCertificateChoiceSessionStatus(HttpSession session) {
        Optional<SessionStatus> certSessionStatus;
        try {
            certSessionStatus = sessionStatusService.getSessionsStatus(session.getId());
        } catch (SessionTimeoutException | UserRefusedException ex) {
            throw new SidOperationException(ex.getMessage());
        }
        return certSessionStatus;
    }

    private DataFile getUploadedDataFile(MultipartFile uploadedFile) {
        try {
            return new DataFile(uploadedFile.getInputStream(), uploadedFile.getOriginalFilename(), uploadedFile.getContentType());
        } catch (IOException e) {
            throw new FileUploadException(e.getCause());
        }
    }

    private static DataToSign toDataToSign(Container container, X509Certificate certificate) {
        return SignatureBuilder.aSignature(container)
                .withSigningCertificate(certificate)
                .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
                .buildDataToSign();
    }

    private static void saveSigningAttributes(HttpSession session,
                                              DataToSign dataToSign,
                                              Container container) {
        session.setAttribute("dataToSign", dataToSign);
        session.setAttribute("container", container);
    }

    private static void saveValidateResponse(HttpSession session, SessionStatus status) {
        try {
            CertificateLevel requestedCertificateLevel = (CertificateLevel) session.getAttribute("signatureCertificateLevel");
            var signatureResponse = SignatureResponseMapper.from(status, requestedCertificateLevel.name());
            session.setAttribute("signatureResponse", signatureResponse);
        } catch (SessionTimeoutException | UserRefusedException | CertificateLevelMismatchException | UserSelectedWrongVerificationCodeException ex) {
            throw new SidOperationException(ex.getMessage());
        }
    }
}
