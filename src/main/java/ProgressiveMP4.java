/**
 * Progressive MP4 Exercise
 */


import com.bitmovin.api.sdk.BitmovinApi;
import com.bitmovin.api.sdk.model.*;
import java.util.*;

public class ProgressiveMP4 {

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
    final String outputPath = "/output/encodings/progressive";
    final String fileName = "progressive_output.mp4";
    final String hostName = "mackenzie-emea.s3.eu-west-1.amazonaws.com";

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
    encoding.setName("MacKenzie Exercise - Progressive MP4");
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

    // create list of streams
    List<Stream> combinedStreams = new ArrayList<>();
    combinedStreams.add(streamVid);
    combinedStreams.add(streamAudio);

    // create encoding output
    EncodingOutput encodingOutput = new EncodingOutput();
    encodingOutput.setOutputPath(outputPath);
    encodingOutput.setOutputId(output.getId());

    // create the MP4 muxing
    Mp4Muxing muxing = new Mp4Muxing();
    muxing.addOutputsItem(encodingOutput);
    muxing.setFilename(fileName);

    for (Stream stream : combinedStreams) {
      MuxingStream muxingStream = new MuxingStream();
      muxingStream.setStreamId(stream.getId());
      muxing.addStreamsItem(muxingStream);
    }

    bitmovinApi.encoding.encodings.muxings.mp4.create(encoding.getId(), muxing);

    // start the encoding
    StartEncodingRequest startEncodingRequest = new StartEncodingRequest();
    bitmovinApi.encoding.encodings.start(encoding.getId(), startEncodingRequest);

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
  }
}
