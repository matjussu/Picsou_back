package com.matjussu.picsou.goal.dto;

import java.util.List;

public record GoalDetailResponse(GoalResponse goal, List<ContributionResponse> contributions) {}
