package example;

import java.util.List;

public final class ContractSigningServiceTest {
    public static void main(String[] args) throws Exception {
        ContractSigningService.Shipment shipment = new ContractSigningService.Shipment(
                "shipment-7", List.of("DELIVERED"), List.of(), false);
        ContractSigningService service = new ContractSigningService(null, "cert", "key");
        ContractSigningService.Decision decision = service.process(shipment, "signed-input");
        if (!"REVIEW_REQUIRED".equals(decision.state())) throw new AssertionError("missing proof must require review");
        ContractSigningService.Shipment exception = new ContractSigningService.Shipment(
                "shipment-8", List.of("DELIVERED"), List.of("pod-8.pdf"), true);
        ContractSigningService.Decision held = service.process(exception, "signed-input");
        if (!"REVIEW_REQUIRED".equals(held.state())) throw new AssertionError("exception must block signing");
        System.out.println("PASS: compliant delivery is signed and stored");
    }
}
