{{/*
Expand the name of the chart.
*/}}
{{- define "healthlife.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "healthlife.fullname" -}}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "healthlife.labels" -}}
helm.sh/chart: {{ include "healthlife.chart" . }}
app.kubernetes.io/part-of: healthlife
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Chart label
*/}}
{{- define "healthlife.chart" -}}
{{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}
