#include "source/extensions/filters/http/set_metadata/set_metadata_filter.h"

#include "envoy/extensions/filters/http/set_metadata/v3/set_metadata.pb.h"

#include "source/common/config/well_known_names.h"
#include "source/common/http/utility.h"
#include "source/common/protobuf/protobuf.h"

#include "absl/strings/str_format.h"

namespace Envoy {
namespace Extensions {
namespace HttpFilters {
namespace SetMetadataFilter {

Config::Config(const envoy::extensions::filters::http::set_metadata::v3::Config& proto_config, const bool per_route) {
  namespace_ = proto_config.metadata_namespace();
  value_ = proto_config.value();

  if (per_route && namespace_.empty()) {
    throw EnvoyException("set_metadata_filter: Per route config must specify metadata_namespace");
  }
}

Config::Config(const absl::string_view metadata_namespace, const ProtobufWkt::Struct& value) {
  namespace_ = metadata_namespace;
  value_ = value;
}

SetMetadataFilter::SetMetadataFilter(const ConfigSharedPtr config) : config_(config) {}

SetMetadataFilter::~SetMetadataFilter() = default;

Http::FilterHeadersStatus SetMetadataFilter::decodeHeaders(Http::RequestHeaderMap&, bool) {
  const auto* config = getConfig();
  ENVOY_LOG(debug, "set_metadata: ns {}\n", config->metadataNamespace());
  const absl::string_view metadata_namespace = config->metadataNamespace();
  auto& metadata = *decoder_callbacks_->streamInfo().dynamicMetadata().mutable_filter_metadata();
  ProtobufWkt::Struct& org_fields =
      metadata[toStdStringView(metadata_namespace)]; // NOLINT(std::string_view)
  const ProtobufWkt::Struct& to_merge = config->value();

  StructUtil::update(org_fields, to_merge);

  return Http::FilterHeadersStatus::Continue;
}

Http::FilterDataStatus SetMetadataFilter::decodeData(Buffer::Instance&, bool) {
  return Http::FilterDataStatus::Continue;
}

void SetMetadataFilter::setDecoderFilterCallbacks(Http::StreamDecoderFilterCallbacks& callbacks) {
  decoder_callbacks_ = &callbacks;
}

const Config* SetMetadataFilter::getConfig() const {
  if (effective_config_) {
    return effective_config_;
  }

  const Config* route_config = Http::Utility::resolveMostSpecificPerFilterConfig<Config>(decoder_callbacks_);

  if (route_config) {
    const auto base_config = config_.get();
    const absl::string_view base_namespace = base_config->metadataNamespace();
    const absl::string_view route_namespace = route_config->metadataNamespace();
    if (base_namespace == route_namespace) {
      const ProtobufWkt::Struct& base_values = base_config->value();
      const ProtobufWkt::Struct& route_values = route_config->value();
      ProtobufWkt::Struct new_values = {};
      StructUtil::update(new_values, base_values);
      StructUtil::update(new_values, route_values);
      config_ = std::make_shared<Config>(Config(route_namespace, new_values));
      effective_config_ = config_.get();
    }
    return effective_config_;
  }

  effective_config_ = config_.get();
  return effective_config_;
}

} // namespace SetMetadataFilter
} // namespace HttpFilters
} // namespace Extensions
} // namespace Envoy
