package com.nudgebank.bankbackend.card.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market")
@Getter
@NoArgsConstructor
public class Market {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "market_id")
  private Long marketId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id", nullable = false)
  private MarketCategory category;

  @Column(name = "market_name", nullable = false, length = 100)
  private String marketName;

  private Market(MarketCategory category, String marketName) {
    this.category = category;
    this.marketName = marketName;
  }

  public static Market create(MarketCategory category, String marketName) {
    return new Market(category, marketName);
  }
}
