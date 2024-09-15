package com.exechange_test.core.common.api.reports;

import java.util.Optional;

public interface ReportQueriesHandler {

    <R extends ReportResult> Optional<R> handleReport(ReportQuery<R> reportQuery);

}
