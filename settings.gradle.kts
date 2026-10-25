rootProject.name = "flowguard-ai"

include("fixture-wallet")
include("trusted-tests")
include("backend")
include("auth-server")
include("api-gateway")
include("integration")
include("benchmarks:baseline")

project(":benchmarks:baseline").projectDir = file("benchmarks/baseline")
