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

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import ee.sk.smartid.CertificateLevel;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.exception.permanent.SmartIdClientException;
import ee.sk.smartid.exception.useraccount.DocumentUnusableException;
import ee.sk.smartid.rest.dao.CertificateByDocumentNumberResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmartIdCertificateChoiceByDocumentNumberService {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdCertificateChoiceByDocumentNumberService.class);

    private final SmartIdClient smartIdClient;

    @Value("${sid.client.relyingPartyUuid}")
    private String relyingPartyUUID;

    @Value("${sid.client.relyingPartyName}")
    private String relyingPartyName;

    public SmartIdCertificateChoiceByDocumentNumberService(SmartIdClient smartIdClient) {
        this.smartIdClient = smartIdClient;
    }

    public CertificateByDocumentNumberResult getCertificate(String documentNumber) {
        //TODO: 26.06.2025 remove this if block when CerticificateChoiceByDocumentNumber endpoint is supported by Smart-ID demo environment
        if (isMockDocumentNumber(documentNumber)) {
            return getFakeCertificateResult(documentNumber);
        }
        try {
            return smartIdClient
                    .createCertificateByDocumentNumber()
                    .withDocumentNumber(documentNumber)
                    .withRelyingPartyUUID(relyingPartyUUID)
                    .withRelyingPartyName(relyingPartyName)
                    .withCertificateLevel(CertificateLevel.QUALIFIED)
                    .getCertificateByDocumentNumber();
        } catch (DocumentUnusableException ex) {
            logger.warn("Document is unusable for documentNumber {}: {}", documentNumber, ex.getMessage());
            throw ex;
        } catch (SmartIdClientException ex) {
            logger.error("SmartIdClient misconfiguration or parameter error for documentNumber {}: {}", documentNumber, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving certificate for documentNumber {}: {}", documentNumber, ex.getMessage(), ex);
            throw new RuntimeException("Unexpected error while retrieving certificate", ex);
        }
    }

    //TODO: 26.06.2025 remove this method, when CerticificateChoiceByDocumentNumber endpoint is supported by Smart-ID demo environment
    private boolean isMockDocumentNumber(String documentNumber) {
        return documentNumber != null && documentNumber.endsWith("-MOCK-Q");
    }

    //TODO: 26.06.2025 remove this method, when CerticificateChoiceByDocumentNumber endpoint is supported by Smart-ID demo environment
    private CertificateByDocumentNumberResult getFakeCertificateResult(String documentNumber) {
        logger.info("Returning fake certificate for mock documentNumber {}", documentNumber);
        return new CertificateByDocumentNumberResult(CertificateLevel.QUALIFIED, loadCertificateFromKeyStore("sid.trusted_root_certs.p12", "changeit"));
    }

    //TODO: 26.06.2025 remove this method, when CerticificateByDocumentNumber endpoint is supported by Smart-ID demo environment
    private X509Certificate loadCertificateFromKeyStore(String p12File, String password) {
        try (InputStream keystoreStream = getClass().getClassLoader().getResourceAsStream(p12File)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(keystoreStream, password.toCharArray());

            return (X509Certificate) keyStore.getCertificate(keyStore.aliases().nextElement());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load certificate from " + p12File, e);
        }
    }
}
