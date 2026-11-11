package com.ucms.academic.web.dto;

/** What the caller may do with one course offering's delivery (content, assignments,
 * grading): staffView — sees it as staff; manage — may change it. Students are neither
 * (their access is decided by enrollment, in the services that own it). */
public record OfferingAccessResponse(boolean staffView, boolean manage) {}
