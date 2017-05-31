package data;

import java.util.List;
import java.util.Map;

/**
 * Created by flowmaster on 27.05.17.
 */
public class GetCertificates {
    private List<CertificateItem> certificates;

    public List<CertificateItem> getCertificates() {
        return certificates;
    }

    public void setCertificates(List<CertificateItem> certificates) {
        this.certificates = certificates;
    }

    public static class CertificateItem{
        private String cert_img;
        private String cert_title;

        public CertificateItem(String cert_img, String cert_title) {
            this.cert_img = cert_img;
            this.cert_title = cert_title;
        }

        public String getCert_img() {

            return cert_img;
        }

        public void setCert_img(String cert_img) {
            this.cert_img = cert_img;
        }

        public String getCert_title() {
            return cert_title;
        }

        public void setCert_title(String cert_title) {
            this.cert_title = cert_title;
        }
    }
}
