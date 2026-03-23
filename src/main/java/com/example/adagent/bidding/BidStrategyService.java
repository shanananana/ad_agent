package com.example.adagent.bidding;

import com.example.adagent.bidding.dto.BaseBidModelFile;
import com.example.adagent.bidding.dto.CoefficientsFile;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 出价策略<strong>查询门面</strong>：按人群×时段×设备读取 {@link com.example.adagent.bidding.dto.BaseBidModelFile} 与
 * {@link com.example.adagent.bidding.dto.CoefficientsFile}，计算生效价 B×α；本仓库不设 RTB 实时竞价，仅供演示或后续扩展。
 */
@Service
public class BidStrategyService {

    private final BidStrategyRepository repository;

    public BidStrategyService(BidStrategyRepository repository) {
        this.repository = repository;
    }

    public double getBaseBid(String audience, int hourSlot, String device) {
        return findBaseEntry(audience, hourSlot, device)
                .map(BaseBidModelFile.BaseBidEntry::getBaseBid)
                .orElse(1.0);
    }

    public double getCoefficient(String audience, int hourSlot, String device) {
        return findCoeffEntry(audience, hourSlot, device)
                .map(CoefficientsFile.CoefficientEntry::getAlpha)
                .orElse(1.0);
    }

    /** 实时层可将「模型出价」再乘以此值，或等价地理解为缩放因子 */
    public double getEffectiveBaseBid(String audience, int hourSlot, String device) {
        return getBaseBid(audience, hourSlot, device) * getCoefficient(audience, hourSlot, device);
    }

    private Optional<BaseBidModelFile.BaseBidEntry> findBaseEntry(String audience, int hourSlot, String device) {
        BaseBidModelFile f = repository.loadBaseModel();
        if (f.getEntries() == null) {
            return Optional.empty();
        }
        return f.getEntries().stream()
                .filter(e -> match(e.getAudience(), audience) && e.getHourSlot() == hourSlot
                        && match(e.getDevice(), device))
                .findFirst();
    }

    private Optional<CoefficientsFile.CoefficientEntry> findCoeffEntry(String audience, int hourSlot, String device) {
        CoefficientsFile f = repository.loadCoefficients();
        if (f.getEntries() == null) {
            return Optional.empty();
        }
        return f.getEntries().stream()
                .filter(e -> match(e.getAudience(), audience) && e.getHourSlot() == hourSlot
                        && match(e.getDevice(), device))
                .findFirst();
    }

    private static boolean match(String a, String b) {
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b.trim());
    }
}
