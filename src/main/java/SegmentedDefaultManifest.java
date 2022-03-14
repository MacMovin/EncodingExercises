import com.bitmovin.api.sdk.BitmovinApi;
import com.bitmovin.api.sdk.model.*;

public class SegmentedDefaultManifest {
  /**
   * Bunch of variables
   */
  private static BitmovinApi bitmovinApi;
  private static String myApiKey;
  private static String myS3BucketName;
  private static String myS3AccessKey;
  private static String myS3SecretKey;
  private static final int videoHeight = 720;
  private static final long videoBitrate = 4000000L;
  private static final long audioBitrate = 128000L;

  /**
   * Get a variable from the environment and throw exception if it doesn't exist.
   */
  private static String macGetEnv(String name) throws RuntimeException {
    String value = System.getenv(name);
    if (value == null) {
      throw new RuntimeException("Can't get "+name);
    }
    return value;
  }

  /**
   * Main entry point
   */
  public static void main(String[] args) throws Exception {

    // some variables
    final String inputPath = "/input/flower_show_1080p.mov";
    final String outputPath = "/output/encodings/segmented_default_manifest";
    final String fileName = "segmented_output.mpd";
    final String hostName = "mackenzie-emea.s3.eu-west-1.amazonaws.com";
    final double segmentLength = 4.0;

    // get some config stuff from environment variables.
    myApiKey = macGetEnv("BITMOVIN_API_KEY");
    myS3BucketName = macGetEnv("BITMOVIN_S3_BUCKET_NAME");
    myS3AccessKey = macGetEnv("BITMOVIN_S3_ACCESS_KEY");
    myS3SecretKey = macGetEnv("BITMOVIN_S3_SECRET_KEY");

    // create API
    BitmovinApi bitmovinApi = BitmovinApi.builder().withApiKey(myApiKey).build();

    // create the input
    HttpInput input = new HttpInput();
    input.setHost(hostName);
    input = bitmovinApi.encoding.inputs.http.create(input);

    // create the output
    S3Output output = new S3Output();
    output.setBucketName(myS3BucketName);
    output.setAccessKey(myS3AccessKey);
    output.setSecretKey(myS3SecretKey);
    output = bitmovinApi.encoding.outputs.s3.create(output);

    // create the encoding
    Encoding encoding = new Encoding();
    encoding.setCloudRegion(CloudRegion.AUTO);
    encoding.setEncoderVersion("LATEST");
    encoding.setName("MacKenzie Exercise - Segmented with Default Manifest");
    encoding = bitmovinApi.encoding.encodings.create(encoding);

    // create the H264 video config
    H264VideoConfiguration videoConfiguration = new H264VideoConfiguration();
    videoConfiguration.setName(String.format("H.264 %dp", videoHeight));
    videoConfiguration.setPresetConfiguration(PresetConfiguration.VOD_STANDARD);
    videoConfiguration.setHeight(videoHeight);
    videoConfiguration.setBitrate(videoBitrate);
    videoConfiguration =
        bitmovinApi.encoding.configurations.video.h264.create(videoConfiguration);

    // create the AAC audio config
    AacAudioConfiguration audioConfiguration = new AacAudioConfiguration();
    audioConfiguration.setName(String.format("AAC %d kbit/s", audioBitrate));
    audioConfiguration.setBitrate(audioBitrate);
    audioConfiguration = bitmovinApi.encoding.configurations.audio.aac.create(audioConfiguration);

    // create input stream
    StreamInput streamInput = new StreamInput();
    streamInput.setInputId(input.getId());
    streamInput.setInputPath(inputPath);
    streamInput.setSelectionMode(StreamSelectionMode.AUTO);

    // set video stream
    Stream streamVid = new Stream();
    streamVid.addInputStreamsItem(streamInput);
    streamVid.setCodecConfigId(videoConfiguration.getId());
    streamVid.setMode(StreamMode.STANDARD);

    // set audio stream
    Stream streamAudio = new Stream();
    streamAudio.addInputStreamsItem(streamInput);
    streamAudio.setCodecConfigId(audioConfiguration.getId());
    streamAudio.setMode(StreamMode.STANDARD);

    // create the streams
    streamVid = bitmovinApi.encoding.encodings.streams.create(encoding.getId(), streamVid);
    streamAudio = bitmovinApi.encoding.encodings.streams.create(encoding.getId(), streamAudio);

    // create encoding output
    EncodingOutput encodingOutput = new EncodingOutput();
    encodingOutput.setOutputPath(outputPath);
    encodingOutput.setOutputId(output.getId());

    // create the fmp4 video muxing
    MuxingStream muxingStreamVid = new MuxingStream();
    muxingStreamVid.setStreamId(streamVid.getId());

    Fmp4Muxing muxingVid = new Fmp4Muxing();
    encodingOutput.setOutputPath(outputPath + "/video");
    muxingVid.addOutputsItem(encodingOutput);
    muxingVid.setSegmentLength(segmentLength);
    muxingVid.addStreamsItem(muxingStreamVid);
    bitmovinApi.encoding.encodings.muxings.fmp4.create(encoding.getId(), muxingVid);

    // create audio muxing
    MuxingStream muxingStreamAudio = new MuxingStream();
    muxingStreamAudio.setStreamId(streamAudio.getId());

    Fmp4Muxing muxingAudio = new Fmp4Muxing();
    encodingOutput.setOutputPath(outputPath + "/audio");
    muxingAudio.addOutputsItem(encodingOutput);
    muxingAudio.setSegmentLength(segmentLength);
    muxingAudio.addStreamsItem(muxingStreamAudio);
    bitmovinApi.encoding.encodings.muxings.fmp4.create(encoding.getId(), muxingAudio);

    // start the encoding
    StartEncodingRequest startEncodingRequest = new StartEncodingRequest();
    bitmovinApi.encoding.encodings.start(encoding.getId(), startEncodingRequest);

    // create the DASH manifest
    DashManifestDefault dashManifestDefault = new DashManifestDefault();
    dashManifestDefault.setEncodingId(encoding.getId());
    dashManifestDefault.setManifestName(fileName);
    dashManifestDefault.setVersion(DashManifestDefaultVersion.V1);
    encodingOutput.setOutputPath(outputPath);
    dashManifestDefault.addOutputsItem(encodingOutput);
    dashManifestDefault = bitmovinApi.encoding.manifests.dash.defaultapi.create(dashManifestDefault);

    // wait for it to be done
    Task task;
    do {
      Thread.sleep(5000);
      task = bitmovinApi.encoding.encodings.status(encoding.getId());
    } while (task.getStatus() != Status.FINISHED
        && task.getStatus() != Status.ERROR
        && task.getStatus() != Status.CANCELED);

    if (task.getStatus() == Status.ERROR) {
      throw new RuntimeException("Encoding failed");
    }

    bitmovinApi.encoding.manifests.dash.start(dashManifestDefault.getId());
  }
}
