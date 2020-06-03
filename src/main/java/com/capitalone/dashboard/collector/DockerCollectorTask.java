
package com.capitalone.dashboard.collector;

// Import Static
import static com.capitalone.dashboard.model.DockerCollectorItem.API_TOKEN;
import static com.capitalone.dashboard.model.DockerCollectorItem.INSTANCE_PORT;
import static com.capitalone.dashboard.model.DockerCollectorItem.INSTANCE_URL;

import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.CPUStats;
import com.capitalone.dashboard.model.CPUStats.CPUStatsUsage;
import com.capitalone.dashboard.model.CPUStats.MemoryStats;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Container;
import com.capitalone.dashboard.model.Container.Bridge;
import com.capitalone.dashboard.model.Container.Mount;
import com.capitalone.dashboard.model.Network;
import com.capitalone.dashboard.model.Node;
import com.capitalone.dashboard.model.Node.ManagerStatus;
import com.capitalone.dashboard.model.Node.Spec;
import com.capitalone.dashboard.model.Processes;
import com.capitalone.dashboard.model.Task;
import com.capitalone.dashboard.model.Task.Status;
import com.capitalone.dashboard.model.Volume;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.CPUStatsRepository;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.ContainerRepository;
import com.capitalone.dashboard.repository.DockerCollectorItemRepository;
import com.capitalone.dashboard.repository.ImageRepository;
import com.capitalone.dashboard.repository.NetworkRepository;
import com.capitalone.dashboard.repository.NodeRepository;
import com.capitalone.dashboard.repository.ProcessesRepository;
import com.capitalone.dashboard.repository.TaskRepository;
import com.capitalone.dashboard.repository.VolumeRepository;

/**
 * CollectorTask that fetches Terraform(Organization, workspace, run) details
 * from Terrafrom Cloud
 */
@Component
public class DockerCollectorTask extends CollectorTask<Collector> {
	private static final Log LOG = LogFactory.getLog(DockerCollectorTask.class);

	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); // 2020-06-01T19:04:23Z

	private final DockerCollectorItemRepository terraformCollectoritemRepository;
	private final CollectorItemRepository collectoritemRepository;
	private final DefaultDockerClient terraformClient;
	private final ComponentRepository dbComponentRepository;
	private final BaseCollectorRepository<Collector> baseCollectorRepository;
	private final DockerSettings terraformSetting;
	private final ImageRepository imageRepository;
	private final NetworkRepository networkRepository;
	private final NodeRepository nodeRepository;
	private final ProcessesRepository processesRepository;
	private final TaskRepository taskRepository;
	private final VolumeRepository volumeRepository;
	private final ContainerRepository containerRepository;
	private final CPUStatsRepository cpuStatsRepository;

	ResponseEntity<String> responseJSON;

	JSONParser jsonParser = new JSONParser();

	@Autowired
	public DockerCollectorTask(TaskScheduler taskScheduler, BaseCollectorRepository<Collector> baseCollectorRepository,
			DockerCollectorItemRepository terraformCollectoritemRepository, DefaultDockerClient terraformClient,
			DockerSettings terraformSetting, ComponentRepository dbComponentRepository,
			CollectorItemRepository collectoritemRepository, ImageRepository imageRepository,
			NetworkRepository networkRepository, NodeRepository nodeRepository, ProcessesRepository processesRepository,
			 VolumeRepository volumeRepository, ContainerRepository containerRepository,
			 CPUStatsRepository cpuStatsRepository,TaskRepository taskRepository) {
		super(taskScheduler, "Docker");
		this.cpuStatsRepository = cpuStatsRepository;
		
		this.terraformClient = terraformClient;
		this.baseCollectorRepository = baseCollectorRepository;
		this.terraformCollectoritemRepository = terraformCollectoritemRepository;

		this.dbComponentRepository = dbComponentRepository;
		this.collectoritemRepository = collectoritemRepository;
		this.terraformSetting = terraformSetting;
		this.imageRepository = imageRepository;
		this.networkRepository = networkRepository;
		this.nodeRepository = nodeRepository;
		this.processesRepository = processesRepository;
		this.taskRepository = taskRepository;
		this.volumeRepository = volumeRepository;
		this.containerRepository = containerRepository;
	}

	@Override
	public Collector getCollector() {
		Collector protoType = new Collector();
		protoType.setName("Docker");
		protoType.setCollectorType(CollectorType.Docker);
		protoType.setOnline(true);
		protoType.setEnabled(true);

		Map<String, Object> allOptions = new HashMap<>();
		allOptions.put(API_TOKEN, "");
		allOptions.put(INSTANCE_PORT, "");
		allOptions.put(INSTANCE_URL, "");
		protoType.setAllFields(allOptions);

		Map<String, Object> uniqueOptions = new HashMap<>();
		uniqueOptions.put(API_TOKEN, "");
		uniqueOptions.put(INSTANCE_PORT, "");
		uniqueOptions.put(INSTANCE_URL, "");

		protoType.setUniqueFields(uniqueOptions);

		return protoType;
	}

	@Override
	public BaseCollectorRepository<Collector> getCollectorRepository() {
		return baseCollectorRepository;
	}

	@Override
	public String getCron() {

		//return "* * * * * ?";
return terraformSetting.getCron();
	}

	@Override
	public void collect(Collector collector) {
		/*
		
		*//**
			 * Logic: Get all the collector items from the collector_item collection for
			 * this collector. Find the organization, Workspace and run details. Save or
			 * update in DB In case of any error add it to collection error of collector
			 * item
			 */

		List<ObjectId> objectIds = new ArrayList<ObjectId>();
		objectIds.add(collector.getId());

		String instanceUri = "http://3.15.29.145";
		String instancePort = "5555";
		String dockerApiVersion = terraformSetting.getDockerApiVersion();
		String[] statsUri = terraformSetting.getDockerStatsUri().split(",");
		JSONObject data = null;

		JSONArray array = null;

		for (String uri : statsUri) {
			array = null;
			data = null;
			String genericURL = instanceUri + ":" + instancePort + "/" + dockerApiVersion + "/" + uri;

			try {

				switch (uri.toUpperCase()) {
				case "VOLUMES":
					data = terraformClient.getDataAsObject(genericURL);
					saveVolumeIfNotExists(data);
					break;

				case "NETWORKS":
					array = terraformClient.getDataAsArray(genericURL);
					saveNetworkIfNotExists(array);
					break;

				case "NODES":
					array = terraformClient.getDataAsArray(genericURL);
					saveNodeIfNotExists(array);
					break;

				case "TASKS":
					array = terraformClient.getDataAsArray(genericURL);
					saveTaskIfNotExists(array);
					break;

				case "SERVICES":
					//data = terraformClient.getDataAsObject(genericURL);
					// saveServiceIfNotExists(data);
					break;

				case "CONTAINERS":
					String URL = genericURL + "/" + "json";
					array = terraformClient.getDataAsArray(URL);

					List<String> ids = saveContainerIfNotExists(array);

					if (ids != null) {
						for (String containerId : ids) {
							
						String[] cpuInsight = terraformSetting.getDockerStatsContainerProcesses().split(",");
						for (String comands : cpuInsight) {

							switch (comands.toUpperCase()) {

							case "TOP":
								URL = genericURL + "/" + containerId + "/" + comands;
								data = terraformClient.getDataAsObject(URL);
								System.out.println();
								saveOrUpdateTopProcesses(data, containerId);

								break;

							case "STATS":
								URL = genericURL + "/" + containerId + "/" + comands + "?stream=false";
								data = terraformClient.getDataAsObject(URL);

								saveOrUpdateCPUInfo(data);

								break;

							default:
								break;
							}
						}
						}
					}

					break;

				default:
					break;
				}

			} catch (RestClientException e) {
				e.printStackTrace();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			} catch (HygieiaException e) {

			}

		}

	}

	private List<String> saveContainerIfNotExists(JSONArray jsonArray) {

		JSONObject jsonObject = null;
		List<String> ids = new ArrayList<String>();
		if (jsonArray != null) {
			for (int i = 0; i < jsonArray.size(); i++) {
				jsonObject = (JSONObject) jsonArray.get(i);
				String Id = (String) jsonObject.get("Id");
				Container container =		containerRepository.findByContainerId(Id);

				if (container == null) {
					container = new Container();
				}
				 container.setStatus(jsonObject.get("Status").toString());
				 //container.setNames(  (String[]) jsonObject.get("Names"));

				 container.setContainerId(Id);
				 container.setImage(jsonObject.get("Image").toString());
				 container.setState(jsonObject.get("State").toString());
				 
				 JSONObject networkSettingsObj = (JSONObject)  jsonObject.get("NetworkSettings");
				 
				 JSONObject networkObj = (JSONObject) networkSettingsObj.get("Networks");
				 
				 JSONObject bridgeObj = (JSONObject) networkObj.get("bridge");
				 
				 Bridge bridge = container.new Bridge();
				 
				 bridge.setGateway(bridgeObj.get("Gateway") == null ? null : bridgeObj.get("Gateway").toString()); 
				 bridge.setIPAddress(bridgeObj.get("IPAddress") == null ? null : bridgeObj.get("IPAddress").toString());
				 bridge.setIPAMConfig(bridgeObj.get("IPAMConfig") == null ? null : bridgeObj.get("IPAMConfig").toString());
				 bridge.setIPPrefixLen(bridgeObj.get("IPPrefixLen") == null ? null : bridgeObj.get("IPPrefixLen").toString());
				 bridge.setIPv6Gateway(bridgeObj.get("IPv6Gateway") == null ? null : bridgeObj.get("IPv6Gateway").toString());
				 bridge.setLinks(bridgeObj.get("Links") == null ? null : bridgeObj.get("Links").toString());
				 bridge.setMacAddress(bridgeObj.get("MacAddress") == null ? null : bridgeObj.get("MacAddress").toString());
				 bridge.setNetworkID(bridgeObj.get("NetworkID") == null ? null : bridgeObj.get("NetworkID").toString());
				 
				 container.setBridge(bridge);
				 
				
				 
				 JSONArray mounts = (JSONArray)  jsonObject.get("Mounts");
				 
				 for (int j = 0; j < mounts.size(); j++) {
					 JSONObject object = (JSONObject) mounts.get(j);
					 Mount mount = container.new Mount();
					 mount.setDestination(object.get("Destination") == null ? null : object.get("Destination").toString());
					 mount.setDriver(object.get("Driver") == null ? null : object.get("Driver").toString());
					 mount.setMode(object.get("Mode") == null ? null : object.get("Mode").toString());
					 mount.setName(object.get("Name") == null ? null : object.get("Name").toString());
					 mount.setPropagation(object.get("Propagation") == null ? null : object.get("Propagation").toString());
					 mount.setRw(object.get("RW") == null ? null : object.get("RW").toString());
					 mount.setSource(object.get("Source") == null ? null : object.get("Source").toString());
					 mount.setType(object.get("Type") == null ? null : object.get("Type").toString());
					 container.getMounts().add(mount);
				}
				 
				container.setCreated(new Date((Long) jsonObject.get("Created")));

				containerRepository.save(container);
				
				ids.add(Id);
			}

		}
		return ids;

	}

	/**
	 * @param jsonArray
	 * @throws ParseException 
	 */
	private void saveNodeIfNotExists(JSONArray jsonArray) throws ParseException {
		JSONObject jsonObject;
		
		if (jsonArray != null) {
			for (int i = 0; i < jsonArray.size(); i++) {
				jsonObject = (JSONObject) jsonArray.get(i);
				String nodeId = (String) jsonObject.get("ID");
				Node node = nodeRepository.findByNodeId(nodeId);

				if (node == null) {
					node = new Node();
				}

				node.setCreatedat((dateFormat.parse(((String) jsonObject.get("CreatedAt")).substring(0,19))));
				node.setUpdatedat((dateFormat.parse(((String) jsonObject.get("UpdatedAt")).substring(0,19))));
				
				Spec spec = node.new Spec();
				JSONObject specObject = (JSONObject) jsonObject.get("Spec");
				if(specObject!=null) {
					spec.setAvailability(specObject.get("Availability").toString());
					spec.setRole(specObject.get("Role").toString());
				}
				
				com.capitalone.dashboard.model.Node.Status status = node.new Status();
				JSONObject statusObject = (JSONObject) jsonObject.get("Status");
				if(statusObject !=null) {
					spec.setAvailability(statusObject .get("State").toString());
					spec.setRole(statusObject .get("Addr").toString());
				}
				

				ManagerStatus manager = node.new ManagerStatus();
				JSONObject managerObject = (JSONObject) jsonObject.get("ManagerStatus");
				if(managerObject!=null) {
					manager.setLeader(managerObject.get("Leader").toString());
					manager.setReachability(managerObject.get("Reachability").toString());
					manager.setAddr(managerObject.get("Addr").toString());
				}
				
				nodeRepository.save(node);
			}

		}

	}

	private void saveOrUpdateCPUInfo(JSONObject data) {
		CPUStats cpuStats = new CPUStats();
		
		JSONObject memoryStatObj = (JSONObject) data.get("memory_stats");
		
		MemoryStats memoryStats = cpuStats.new MemoryStats();
		memoryStats.setLimit( (Long) memoryStatObj.get("limit"));
		memoryStats.setMaxUsage( (Long) memoryStatObj.get("max_usage"));
		memoryStats.setUsage( (Long) memoryStatObj.get("usage"));
		
		cpuStats.setMemoryStats(memoryStats);
		
		JSONObject cpuStatObj = (JSONObject) data.get("cpu_stats");
		
		CPUStatsUsage statsUsage = cpuStats.new CPUStatsUsage();
		
		statsUsage.setSystemCpuUsage( (Long) cpuStatObj.get("system_cpu_usage"));
		statsUsage.setTotalUsage( (Long) cpuStatObj.get("total_usage") );
		
		cpuStats.setStatsUsage(statsUsage);
		cpuStatsRepository.deleteAll();// we only keep the latest
		cpuStatsRepository.save(cpuStats);
		
		
	}

	private void saveOrUpdateTopProcesses(JSONObject data, String containerId) {
		 Processes processes = processesRepository.findByContainerId(containerId);
		 
		 if(processes == null)
			 processes = new Processes();
		 
		 processes.setContainerId(containerId);
		 
		 processes.setProcesses( (JSONArray) data.get("Processes"));
		 processes.setTitles( (JSONArray) data.get("Titles"));
		 
		 processesRepository.save(processes);
		 
		 
		
	}

	private void saveVolumeIfNotExists(JSONObject data) throws ParseException {

		JSONArray jsonArray = terraformClient.getJSONArray(data, "Volumes");
		JSONObject jsonObject = null;
		if (jsonArray != null) {
			for (int i = 0; i < jsonArray.size(); i++) {
				jsonObject = (JSONObject) jsonArray.get(i);
				String name = (String) jsonObject.get("Name");
				Volume volume = volumeRepository.findByName(name);

				if (volume == null) {
					volume = new Volume();
				}

				volume.setCreatedAt((dateFormat.parse(((String) jsonObject.get("CreatedAt")).substring(0,19))));
				volume.setDriver((String) jsonObject.get("Driver"));
				volume.setMountpoint((String) jsonObject.get("Mountpoint"));
				volume.setName((String) jsonObject.get("Name"));
				volume.setScope((String) jsonObject.get("Scope"));
				volumeRepository.save(volume);
			}

		}

	}

	private void saveTaskIfNotExists(JSONArray jsonArray) throws ParseException {

		JSONObject jsonObject = null;
		if (jsonArray != null) {
			for (int i = 0; i < jsonArray.size(); i++) {
				jsonObject = (JSONObject) jsonArray.get(i);
				String Id = (String) jsonObject.get("NodeID");
				Task task = null;
				 taskRepository.findByTaskId(Id);

				if (task == null) {
					task = new Task();
				}
				JSONObject statusObj = (JSONObject) jsonObject.get("Status");

				Status status = task.new Status();

				status.setMessage(statusObj.get("Message").toString());

				status.setState(statusObj.get("State").toString());

				//status.setTimestamp((dateFormat.parse((String) jsonObject.get("Timestamp"))));

				task.setCreatedAt((dateFormat.parse(((String) jsonObject.get("CreatedAt")).substring(0,19))));
				task.setUpdatedAt((dateFormat.parse(((String) jsonObject.get("UpdatedAt")).substring(0,19))));

				taskRepository.save(task);
			}

		}

	}

	private void saveNetworkIfNotExists(JSONArray jsonArray) throws ParseException {
		JSONObject jsonObject = null;
		if (jsonArray != null) {
			for (int i = 0; i < jsonArray.size(); i++) {
				jsonObject = (JSONObject) jsonArray.get(i);
				String name = (String) jsonObject.get("Name");
				String id = (String) jsonObject.get("Id");
				Network network = networkRepository.findByNetworkId(name);

				if (network == null) {
					network = new Network();
				}

				network.setCreated((dateFormat.parse((String) jsonObject.get("Created"))));
				network.setScope((String) jsonObject.get("Scope"));
				network.setNetworkId((String) jsonObject.get("Id"));
				network.setDriver((String) jsonObject.get("Driver"));
				network.setAttachable(Boolean.valueOf(jsonObject.get("Attachable").toString().toUpperCase()));
				network.setIngress(Boolean.valueOf(jsonObject.get("Ingress").toString().toUpperCase()));
				network.setScope((String) jsonObject.get("scope"));
				networkRepository.save(network);
			}
		}
	}


}
