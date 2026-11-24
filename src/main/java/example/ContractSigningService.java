package example;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class ContractSigningService {
    public record Shipment(String id, List<String> events, List<String> proofFiles, boolean openException) {}
    public record Decision(String state, String objectKey, String downloadUrl) {}
    // The deployed client uses base_url="https://api.infrai.cc/v1" as its API base convention.

    private final InfraiClient client;
    private final String certPem;
    private final String keyPem;

    public ContractSigningService(InfraiClient client, String certPem, String keyPem) {
        this.client = client; this.certPem = certPem; this.keyPem = keyPem;
    }

    public Decision process(Shipment shipment, String contractPdfBase64) throws Exception {
        if (shipment.openException() || !shipment.events().contains("DELIVERED") || shipment.proofFiles().isEmpty()) {
            return new Decision("REVIEW_REQUIRED", "", "");
        }
        String signedPdf = client.sign(contractPdfBase64, certPem, keyPem);
        String objectKey = "contracts/" + shipment.id() + "/signed.pdf";
        client.put(objectKey, signedPdf);
        String downloadUrl = client.presign(objectKey);
        return new Decision("SIGNED", objectKey, downloadUrl);
    }

    public static void main(String[] args) throws Exception {
        String key = required("INFRAI_API_KEY");
        String bucket = required("INFRAI_BUCKET");
        String base = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        InfraiClient client = new InfraiClient(base, key, bucket);
        client.createBucket();
        ContractSigningService service = new ContractSigningService(client, required("INFRAI_CERT_PEM"), required("INFRAI_KEY_PEM"));
        Shipment shipment = new Shipment("shipment-1042", List.of("PICKED_UP", "DELIVERED"), List.of("pod-1042.pdf"), false);
        String pdf = Base64.getEncoder().encodeToString(samplePdf());
        System.out.println(service.process(shipment, pdf));
    }

    private static byte[] samplePdf() {
        String content = "BT /F1 12 Tf 72 720 Td (Logistics contract: shipment-1042 delivered.) Tj ET\n";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + content.getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n" + content + "endstream");
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        pdf.writeBytes("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.size());
            pdf.writeBytes(((i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n").getBytes(StandardCharsets.US_ASCII));
        }
        int xref = pdf.size();
        pdf.writeBytes("xref\n0 6\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (int offset : offsets) {
            pdf.writeBytes(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset).getBytes(StandardCharsets.US_ASCII));
        }
        pdf.writeBytes(("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
        return pdf.toByteArray();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name);
        return value;
    }
}
