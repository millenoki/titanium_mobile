var win = Titanium.UI.currentWindow;
win.layout = 'vertical';
win.orientationModes = [Ti.UI.PORTRAIT];
var accelerometerAdded = false;
var velocityLabel = Ti.UI.createLabel({
	text: '  ',
	color: '#000',
	font: {fontSize: 18,fontWeight: 'bold'},
	width: Ti.UI.FILL,
	left: '5'
});
win.add(velocityLabel);

var orientationLabel = Ti.UI.createLabel({
	text: '  ',
	color: '#000',
	font: {fontSize: 18,fontWeight: 'bold'},
	width: Ti.UI.FILL,
	left: '5'
});
win.add(orientationLabel);

var compassTestLabel = Ti.UI.createLabel({
	text: '^',
	color: '#000',
	font: {fontSize: 140,fontWeight: 'bold'}
});
win.add(compassTestLabel);


var accelX = 0, accelY = 0, accelZ = 0;


var magnetometerCallback = function(e) {
	// labels['mag.x'].updatePositionData(e.x);
	// labels['mag.y'].updatePositionData(e.y);
	// labels['mag.z'].updatePositionData(e.z);
};

var currentYaw = 0, currentPitch = 0, currentRoll = 0;
var maxVelocity = 0;
var lastTimestamp = 0;
var gravX = 0, gravY = 0,  gravZ = 0, prevVelocity = 0, prevAcce = 0;
var kFilteringFactor = 0.1;

function tendToZero(value){
	if (value < 0) {
        return Math.ceil(value);
    } else {
        return Math.floor(value);
    }
}

var resetVelocityValue = 40;
var resetVelocityDuration = 400;
var resetYaw = 0;
var resetPitch = 0;
var resetRoll = 0;

var resetTimer = null;
var currentTimestamp = 0;

function computeVelocity(_acc)
{
	if (lastTimestamp === 0)
	{
		lastTimestamp = currentTimestamp;
		return;
	}
	var frequency = 1000.0/ (currentTimestamp - lastTimestamp);

    var vector = Math.sqrt(Math.pow(_acc.x,2)+Math.pow(_acc.y,2)+Math.pow(_acc.z, 2));
    var acce = vector - prevVelocity;
    var velocity = (((acce - prevAcce)/2) * (1/frequency)) + prevVelocity;
    var realVel = velocity*1000;
    if (realVel < resetVelocityValue)
	{
		if (maxVelocity > resetVelocityValue && resetTimer === null)
		{
			var yawDelta = currentYaw - resetYaw;
			var newMax = maxVelocity;
			resetTimer = setTimeout(function(){
				velocityLabel.text = 'LastMax: ' + newMax.toFixed(2);
				if (yawDelta < -0.4)
					velocityLabel.text += '      Right';
				else if(yawDelta > 0.4)
					velocityLabel.text += '      Left';
				else
					velocityLabel.text += '      Center';
				maxVelocity = 0;
				
			}, resetVelocityDuration);
		}
		resetYaw = currentYaw;
		resetPitch = currentPitch;
		resetRoll = currentRoll;
	}
	else
	{
		if (resetTimer !== null)
		{
			clearTimeout(resetTimer);
			resetTimer = null;
		}
		if (realVel > maxVelocity)
		{
			maxVelocity = realVel;
		}
	}

	prevAcce = acce;
	prevVelocity = velocity;
	lastTimestamp = currentTimestamp;
}

var gyroscopeCallback = function(e) {
	currentYaw = e.yaw;
	currentPitch = e.pitch;
	currentRoll = e.roll;
	// orientationLabel.text = 'yaw: ' + e.gyroscope.yaw + '\npitch: ' + e.gyroscope.pitch  + '\nroll: ' + e.gyroscope.roll;
};
var magnetometerCallback = function(e) {
	orientationLabel.text = 'mag.x: ' + e.magnetometer.x + '\nmag.y: ' + e.magnetometer.y  + '\nmag.z: ' + e.magnetometer.z;
};

function removeGravity(_acc)
{
	var gravX = (e.x * kFilteringFactor) + (gravX * (1.0 - kFilteringFactor));
    var gravY = (e.y * kFilteringFactor) + (gravY * (1.0 - kFilteringFactor));
    var gravZ = (e.z * kFilteringFactor) + (gravZ * (1.0 - kFilteringFactor));
    var accelX = e.x - ( (e.x * kFilteringFactor) + (gravX * (1.0 - kFilteringFactor)) );
    var accelY = e.y - ( (e.y * kFilteringFactor) + (gravY * (1.0 - kFilteringFactor)) );
    var accelZ = e.z - ( (e.z * kFilteringFactor) + (gravZ * (1.0 - kFilteringFactor)) );
    accelX *= 9.81;
    accelY *= 9.81;
    accelZ *= 9.81;
    accelX = tendToZero(accelX);
    accelY = tendToZero(accelY);
    accelZ = tendToZero(accelZ);
	return {x:accelX, y:accelY, z:accelZ};
}

var accelerometerCallback = function(e) {
	currentTimestamp = e.timestamp;
	if (Titanium.Platform.name == 'android')
	{
		computeVelocity(removeGravity(e));
	}
	else
	{
		computeVelocity({x:e.ux, y:e.uy, z:e.uz});
	}
};

var motionCallback = function(e) {
	currentTimestamp = e.timestamp;
	currentYaw = e.gyroscope.yaw;
	currentPitch = e.gyroscope.pitch;
	currentRoll = e.gyroscope.roll;
	if (Titanium.Platform.name == 'android')
	{
		computeVelocity(removeGravity(e.accelerometer));
	}
	else
	{
		computeVelocity({x:e.accelerometer.ux, y:e.accelerometer.uy, z:e.accelerometer.uz});
	}
    var heading = Math.atan2(e.rotationMatrix.m22, e.rotationMatrix.m12);
    heading = heading*180/Math.PI;
    // Ti.API.info('heading ' + heading);
	compassTestLabel.transform = Ti.UI.create2DMatrix({rotate:heading});
};


win.addEventListener('close', function(){
	// Ti.Motion.removeEventListener('accelerometer', accelerometerCallback);
	// Ti.Motion.removeEventListener('magnetometer', magnetometerCallback);
	// Ti.Motion.removeEventListener('gyroscope', gyroscopeCallback);
	Ti.Motion.removeEventListener('motion', motionCallback);
});

// Ti.Motion.addEventListener('accelerometer', accelerometerCallback);
// Ti.Motion.addEventListener('magnetometer', magnetometerCallback);
// Ti.Motion.addEventListener('gyroscope', gyroscopeCallback);
Ti.Motion.addEventListener('motion', motionCallback);

if (Titanium.Platform.name == 'iPhone OS' && Titanium.Platform.model == 'Simulator')
{
	var notice = Titanium.UI.createLabel({
		bottom:50,
		font:{fontSize:18},
		color:'#900',
		width:'auto',
		text:'Note: Accelerometer does not work in simulator',
		textAlign:'center'
	});
	win.add(notice);
}

// if (Titanium.Platform.name == 'android')
// {
	Ti.App.addEventListener('pause', function(e) {
		Ti.API.info("removing motion callbacks on pause");
		// Ti.Motion.removeEventListener('accelerometer', accelerometerCallback);
		// Ti.Motion.removeEventListener('magnetometer', magnetometerCallback);
		// Ti.Motion.removeEventListener('gyroscope', gyroscopeCallback);
		Ti.Motion.removeEventListener('motion', motionCallback);
	});
	Ti.App.addEventListener('resume', function(e) {
		Ti.API.info("adding motion callbacks on resume");
		// Ti.Motion.addEventListener('accelerometer', accelerometerCallback);
		// Ti.Motion.addEventListener('magnetometer', magnetometerCallback);
		// Ti.Motion.addEventListener('gyroscope', gyroscopeCallback);
		Ti.Motion.addEventListener('motion', motionCallback);
	});
// }
